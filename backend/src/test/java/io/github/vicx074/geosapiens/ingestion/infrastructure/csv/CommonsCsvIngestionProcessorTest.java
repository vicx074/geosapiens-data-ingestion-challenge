package io.github.vicx074.geosapiens.ingestion.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.vicx074.geosapiens.ingestion.application.InvalidCsvFileException;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowConsumer;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowError;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvTransactionRow;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommonsCsvIngestionProcessorTest {

  private final CommonsCsvIngestionProcessor processor = new CommonsCsvIngestionProcessor();

  @Test
  void shouldStreamAndValidateRowsWithoutBreakingQuotedCsvFields() throws IOException {
    String csv = """
        transaction_id,occurred_at,amount,category
        txn-0001,2025-01-02T03:04:05Z,10.50,"alimentação, especial"
        ,2025-01-02T03:04:05Z,12.00,transporte
        txn-0003,2025-01-02T03:04:05Z,0.00,saúde
        """;
    CollectingConsumer consumer = new CollectingConsumer();

    var summary = processor.process(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), consumer);

    assertThat(summary.acceptedRows()).isEqualTo(1);
    assertThat(summary.rejectedRows()).isEqualTo(2);
    assertThat(summary.processedRows()).isEqualTo(3);
    assertThat(consumer.accepted).singleElement().satisfies(row -> {
      assertThat(row.sourceRow()).isEqualTo(2);
      assertThat(row.transactionId()).isEqualTo("txn-0001");
      assertThat(row.category()).isEqualTo("alimentação, especial");
    });
    assertThat(consumer.rejected)
        .extracting(CsvRowError::code)
        .containsExactly("TRANSACTION_ID_REQUIRED", "AMOUNT_ZERO");
  }

  @Test
  void shouldRejectFileWithUnexpectedHeader() {
    String csv = "id,occurred_at,amount,category\n";

    assertThatExceptionOfType(InvalidCsvFileException.class)
        .isThrownBy(() -> processor.process(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
            CsvRowConsumer.DISCARDING))
        .withMessageContaining("Cabeçalho CSV inválido");
  }

  @Test
  void shouldReadLargeInputInChunksInsteadOfRequestingEntireFile() throws IOException {
    StringBuilder csv = new StringBuilder("transaction_id,occurred_at,amount,category\n");
    for (int index = 1; index <= 20_000; index++) {
      csv.append("txn-").append(index)
          .append(",2025-01-02T03:04:05Z,10.50,transporte\n");
    }
    byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
    TrackingInputStream input = new TrackingInputStream(new ByteArrayInputStream(bytes));

    var summary = processor.process(input, CsvRowConsumer.DISCARDING);

    assertThat(summary.acceptedRows()).isEqualTo(20_000);
    assertThat(input.maxRequestedBytes).isLessThan(bytes.length);
  }

  private static final class CollectingConsumer implements CsvRowConsumer {

    private final List<CsvTransactionRow> accepted = new ArrayList<>();
    private final List<CsvRowError> rejected = new ArrayList<>();

    @Override
    public void accepted(CsvTransactionRow row) {
      accepted.add(row);
    }

    @Override
    public void rejected(CsvRowError error) {
      rejected.add(error);
    }
  }

  private static final class TrackingInputStream extends FilterInputStream {

    private int maxRequestedBytes;

    private TrackingInputStream(ByteArrayInputStream input) {
      super(input);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      maxRequestedBytes = Math.max(maxRequestedBytes, length);
      return super.read(buffer, offset, length);
    }
  }
}
