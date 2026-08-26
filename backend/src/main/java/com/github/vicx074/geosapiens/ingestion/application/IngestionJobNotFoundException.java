package com.github.vicx074.geosapiens.ingestion.application;

import java.util.UUID;

public final class IngestionJobNotFoundException extends RuntimeException {

  public IngestionJobNotFoundException(UUID jobId) {
    super("O job %s não foi encontrado.".formatted(jobId));
  }
}
