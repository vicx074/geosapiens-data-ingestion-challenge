package io.github.vicx074.geosapiens.ingestion.domain;

public final class InvalidIngestionJobTransitionException extends IllegalStateException {

  private final IngestionStatus currentStatus;
  private final IngestionStatus targetStatus;

  public InvalidIngestionJobTransitionException(
      IngestionStatus currentStatus,
      IngestionStatus targetStatus
  ) {
    super("Transição inválida de %s para %s.".formatted(currentStatus, targetStatus));
    this.currentStatus = currentStatus;
    this.targetStatus = targetStatus;
  }

  public IngestionStatus getCurrentStatus() {
    return currentStatus;
  }

  public IngestionStatus getTargetStatus() {
    return targetStatus;
  }
}
