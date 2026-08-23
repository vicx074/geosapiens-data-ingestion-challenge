package com.github.vicx074.geosapiens.ingestion.domain;

public enum IngestionStatus {
  RECEIVED,
  QUEUED,
  PROCESSING,
  COMPLETED,
  COMPLETED_WITH_ERRORS,
  FAILED;

  public boolean isTerminal() {
    return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED;
  }
}
