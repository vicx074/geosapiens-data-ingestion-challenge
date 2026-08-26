package com.github.vicx074.geosapiens.ingestion.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PendingJobPublication(UUID jobId, Instant createdAt) {

  public PendingJobPublication {
    Objects.requireNonNull(jobId, "O identificador do job é obrigatório.");
    Objects.requireNonNull(createdAt, "A data da publicação é obrigatória.");
  }
}
