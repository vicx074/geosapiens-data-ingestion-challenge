package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.util.Objects;
import java.util.UUID;

public record ClaimedJobPublication(UUID jobId, int attempts, UUID claimToken) {

  public ClaimedJobPublication {
    Objects.requireNonNull(jobId, "O identificador do job é obrigatório.");
    Objects.requireNonNull(claimToken, "O token da reivindicação é obrigatório.");
    if (attempts <= 0) {
      throw new IllegalArgumentException("A publicação reivindicada deve possuir uma tentativa.");
    }
  }
}
