package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.util.UUID;

public final class LostOutboxClaimException extends RuntimeException {

  public LostOutboxClaimException(UUID jobId) {
    super("A reivindicação do Outbox para o job %s não está mais ativa.".formatted(jobId));
  }
}
