package io.github.vicx074.geosapiens.ingestion.application.port.out;

public record BatchInsertResult(long acceptedRows, long rejectedRows) {

  public BatchInsertResult {
    if (acceptedRows < 0 || rejectedRows < 0) {
      throw new IllegalArgumentException("Os contadores persistidos do lote não podem ser negativos.");
    }
    Math.addExact(acceptedRows, rejectedRows);
  }

  public long processedRows() {
    return Math.addExact(acceptedRows, rejectedRows);
  }
}
