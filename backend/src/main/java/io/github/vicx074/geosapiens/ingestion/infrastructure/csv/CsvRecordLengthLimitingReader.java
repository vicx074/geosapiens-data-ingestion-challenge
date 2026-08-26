package io.github.vicx074.geosapiens.ingestion.infrastructure.csv;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Interrompe a leitura antes que um único registro CSV possa crescer sem limite em memória.
 *
 * <p>O contador acompanha registros lógicos, não apenas linhas físicas: quebras de linha dentro de
 * campos entre aspas continuam pertencendo ao mesmo registro. A classe não interpreta os valores;
 * apenas reconhece delimitadores e aspas suficientes para impor o limite antes do Commons CSV
 * materializar campos potencialmente gigantes.</p>
 */
final class CsvRecordLengthLimitingReader extends FilterReader {

  private static final char QUOTE = '"';
  private static final char DELIMITER = ',';

  private final int maxRecordCharacters;
  private long recordNumber = 1;
  private int recordCharacters;
  private boolean inQuotedField;
  private boolean pendingQuote;
  private boolean atFieldStart = true;
  private boolean previousSeparatorWasCarriageReturn;

  CsvRecordLengthLimitingReader(Reader delegate, int maxRecordCharacters) {
    super(delegate);
    if (maxRecordCharacters < 1) {
      throw new IllegalArgumentException("O limite do registro CSV deve ser positivo.");
    }
    this.maxRecordCharacters = maxRecordCharacters;
  }

  @Override
  public int read() throws IOException {
    int value = super.read();
    if (value != -1) {
      inspect((char) value);
    }
    return value;
  }

  @Override
  public int read(char[] buffer, int offset, int length) throws IOException {
    int read = super.read(buffer, offset, length);
    for (int index = 0; index < read; index++) {
      inspect(buffer[offset + index]);
    }
    return read;
  }

  private void inspect(char current) throws CsvRecordTooLargeException {
    // Em CRLF, o '\r' já encerrou o registro; o '\n' é apenas a segunda metade do separador.
    if (!inQuotedField && current == '\n' && previousSeparatorWasCarriageReturn) {
      previousSeparatorWasCarriageReturn = false;
      return;
    }

    recordCharacters = Math.addExact(recordCharacters, 1);
    if (recordCharacters > maxRecordCharacters) {
      throw new CsvRecordTooLargeException(recordNumber, maxRecordCharacters);
    }

    if (inQuotedField) {
      inspectQuoted(current);
      return;
    }

    inspectUnquoted(current);
  }

  private void inspectQuoted(char current) {
    if (pendingQuote) {
      if (current == QUOTE) {
        // Duas aspas dentro de um campo quoted representam uma aspa literal.
        pendingQuote = false;
        return;
      }

      inQuotedField = false;
      pendingQuote = false;
      inspectUnquoted(current);
      return;
    }

    if (current == QUOTE) {
      pendingQuote = true;
    }
  }

  private void inspectUnquoted(char current) {
    if (current == '\n') {
      finishRecord();
      return;
    }

    if (current == '\r') {
      finishRecord();
      previousSeparatorWasCarriageReturn = true;
      return;
    }

    previousSeparatorWasCarriageReturn = false;

    if (current == DELIMITER) {
      atFieldStart = true;
      return;
    }

    if (current == QUOTE && atFieldStart) {
      inQuotedField = true;
      atFieldStart = false;
      return;
    }

    atFieldStart = false;
  }

  private void finishRecord() {
    recordCharacters = 0;
    recordNumber = Math.addExact(recordNumber, 1);
    atFieldStart = true;
  }
}
