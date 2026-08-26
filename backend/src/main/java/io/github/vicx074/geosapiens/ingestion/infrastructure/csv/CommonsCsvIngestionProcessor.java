package io.github.vicx074.geosapiens.ingestion.infrastructure.csv;

import io.github.vicx074.geosapiens.ingestion.application.InvalidCsvFileException;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvProcessingSummary;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowConsumer;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowError;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvTransactionRow;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionCsvProcessor;
import io.github.vicx074.geosapiens.ingestion.infrastructure.config.CsvIngestionProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.csv.CSVException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public final class CommonsCsvIngestionProcessor implements IngestionCsvProcessor {

  private static final List<String> EXPECTED_HEADER = List.of(
      "transaction_id", "occurred_at", "amount", "category");

  private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
      .setHeader()
      .setSkipHeaderRecord(true)
      .setIgnoreEmptyLines(false)
      .build();

  private final int maxRecordCharacters;

  public CommonsCsvIngestionProcessor(CsvIngestionProperties properties) {
    this.maxRecordCharacters = Objects.requireNonNull(
        properties,
        "As propriedades de ingestão CSV são obrigatórias.").maxRecordCharacters();
  }

  @Override
  public CsvProcessingSummary process(InputStream content, CsvRowConsumer consumer) throws IOException {
    Objects.requireNonNull(content, "O conteúdo CSV é obrigatório.");
    Objects.requireNonNull(consumer, "O consumidor das linhas é obrigatório.");

    long acceptedRows = 0;
    long rejectedRows = 0;

    try (Reader reader = boundedUtf8Reader(content);
         CSVParser parser = CSVParser.parse(reader, FORMAT)) {
      validateHeader(parser.getHeaderNames());

      for (CSVRecord record : parser) {
        RowValidation validation = validate(record);
        if (validation.accepted() != null) {
          consumer.accepted(validation.accepted());
          acceptedRows = Math.addExact(acceptedRows, 1);
        } else {
          consumer.rejected(validation.rejected());
          rejectedRows = Math.addExact(rejectedRows, 1);
        }
      }
    } catch (CsvRecordTooLargeException exception) {
      throw invalidOversizedRecord(exception);
    } catch (CSVException exception) {
      throw invalidCsvSyntax(exception);
    } catch (CharacterCodingException exception) {
      throw invalidUtf8(exception);
    } catch (UncheckedIOException exception) {
      throw classifyReadFailure(exception.getCause());
    }

    return new CsvProcessingSummary(acceptedRows, rejectedRows);
  }

  private Reader boundedUtf8Reader(InputStream content) {
    var decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    Reader decoded = new InputStreamReader(content, decoder);
    return new CsvRecordLengthLimitingReader(decoded, maxRecordCharacters);
  }

  private static IOException classifyReadFailure(IOException cause) {
    if (cause instanceof CsvRecordTooLargeException tooLarge) {
      throw invalidOversizedRecord(tooLarge);
    }
    if (cause instanceof CSVException csvException) {
      throw invalidCsvSyntax(csvException);
    }
    if (cause instanceof CharacterCodingException codingException) {
      throw invalidUtf8(codingException);
    }
    return cause;
  }

  private static InvalidCsvFileException invalidOversizedRecord(
      CsvRecordTooLargeException exception) {
    return new InvalidCsvFileException(exception.getMessage(), exception);
  }

  private static InvalidCsvFileException invalidCsvSyntax(CSVException exception) {
    return new InvalidCsvFileException(
        "Conteúdo CSV sintaticamente inválido.",
        exception);
  }

  private static InvalidCsvFileException invalidUtf8(CharacterCodingException exception) {
    return new InvalidCsvFileException(
        "O arquivo CSV deve possuir codificação UTF-8 válida.",
        exception);
  }

  private static void validateHeader(List<String> actualHeader) {
    if (!EXPECTED_HEADER.equals(actualHeader)) {
      throw new InvalidCsvFileException(
          "Cabeçalho CSV inválido. Esperado %s e recebido %s."
              .formatted(EXPECTED_HEADER, actualHeader));
    }
  }

  private static RowValidation validate(CSVRecord record) {
    long sourceRow = Math.addExact(record.getRecordNumber(), 1);
    if (record.size() != EXPECTED_HEADER.size()) {
      return rejected(sourceRow, "COLUMN_COUNT", "A linha deve possuir exatamente 4 colunas.");
    }

    String transactionId = record.get(0);
    if (transactionId.isBlank()) {
      return rejected(sourceRow, "TRANSACTION_ID_REQUIRED", "transaction_id é obrigatório.");
    }
    if (transactionId.length() > 64) {
      return rejected(sourceRow, "TRANSACTION_ID_TOO_LONG", "transaction_id excede 64 caracteres.");
    }

    Instant occurredAt;
    try {
      OffsetDateTime parsedDate = OffsetDateTime.parse(record.get(1));
      if (!parsedDate.getOffset().equals(ZoneOffset.UTC)) {
        return rejected(sourceRow, "OCCURRED_AT_NOT_UTC", "occurred_at deve estar em UTC.");
      }
      occurredAt = parsedDate.toInstant();
    } catch (DateTimeParseException exception) {
      return rejected(sourceRow, "OCCURRED_AT_INVALID", "occurred_at deve usar ISO 8601.");
    }

    BigDecimal amount;
    try {
      amount = new BigDecimal(record.get(2));
    } catch (NumberFormatException exception) {
      return rejected(sourceRow, "AMOUNT_INVALID", "amount deve ser decimal.");
    }
    if (amount.scale() != 2) {
      return rejected(sourceRow, "AMOUNT_SCALE_INVALID", "amount deve possuir duas casas decimais.");
    }
    if (amount.precision() > 19) {
      return rejected(sourceRow, "AMOUNT_PRECISION_INVALID", "amount excede a precisão NUMERIC(19,2).");
    }
    if (amount.compareTo(BigDecimal.ZERO) == 0) {
      return rejected(sourceRow, "AMOUNT_ZERO", "amount deve ser diferente de zero.");
    }

    String category = record.get(3);
    if (category.isBlank()) {
      return rejected(sourceRow, "CATEGORY_REQUIRED", "category é obrigatória.");
    }
    if (category.length() > 100) {
      return rejected(sourceRow, "CATEGORY_TOO_LONG", "category excede 100 caracteres.");
    }

    return new RowValidation(
        new CsvTransactionRow(sourceRow, transactionId, occurredAt, amount, category), null);
  }

  private static RowValidation rejected(long sourceRow, String code, String reason) {
    return new RowValidation(null, new CsvRowError(sourceRow, code, reason));
  }

  private record RowValidation(CsvTransactionRow accepted, CsvRowError rejected) {
  }
}
