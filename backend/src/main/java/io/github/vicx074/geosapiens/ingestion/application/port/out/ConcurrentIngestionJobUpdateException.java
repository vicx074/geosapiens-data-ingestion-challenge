package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.util.UUID;

public final class ConcurrentIngestionJobUpdateException extends RuntimeException {

  public ConcurrentIngestionJobUpdateException(UUID jobId, long expectedVersion) {
    super("O job %s não está mais na versão %d.".formatted(jobId, expectedVersion));
  }
}
