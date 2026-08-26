package io.github.vicx074.geosapiens.ingestion.application;

import java.util.UUID;

public class IngestionProcessingException extends RuntimeException {

  public IngestionProcessingException(UUID jobId, Throwable cause) {
    super("Falha de infraestrutura durante o processamento do job %s.".formatted(jobId), cause);
  }
}
