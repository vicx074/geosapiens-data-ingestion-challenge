package io.github.vicx074.geosapiens.ingestion.application;

public record IngestionBatchPolicy(int batchSize) {

  public IngestionBatchPolicy {
    if (batchSize < 1 || batchSize > 100_000) {
      throw new IllegalArgumentException("O tamanho do lote deve estar entre 1 e 100000 linhas.");
    }
  }
}
