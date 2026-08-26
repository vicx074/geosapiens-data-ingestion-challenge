package io.github.vicx074.geosapiens.ingestion.application.port.out;

public record CsvProcessingSummary(long acceptedRows, long rejectedRows) {

  public CsvProcessingSummary {
    if (acceptedRows < 0 || rejectedRows < 0) {
      throw new IllegalArgumentException("Os contadores do processamento não podem ser negativos.");
    }
    Math.addExact(acceptedRows, rejectedRows);
  }

  public long processedRows() {
    return Math.addExact(acceptedRows, rejectedRows);
  }
}
