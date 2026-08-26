package io.github.vicx074.geosapiens.ingestion.infrastructure.csv;

import java.io.IOException;

final class CsvRecordTooLargeException extends IOException {

  private final long recordNumber;
  private final int maxRecordCharacters;

  CsvRecordTooLargeException(long recordNumber, int maxRecordCharacters) {
    super("O registro CSV %d excede o limite de %d caracteres."
        .formatted(recordNumber, maxRecordCharacters));
    this.recordNumber = recordNumber;
    this.maxRecordCharacters = maxRecordCharacters;
  }

  long recordNumber() {
    return recordNumber;
  }

  int maxRecordCharacters() {
    return maxRecordCharacters;
  }
}
