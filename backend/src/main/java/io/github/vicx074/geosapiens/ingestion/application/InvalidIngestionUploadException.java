package io.github.vicx074.geosapiens.ingestion.application;

public final class InvalidIngestionUploadException extends RuntimeException {

  public InvalidIngestionUploadException(String message) {
    super(message);
  }
}
