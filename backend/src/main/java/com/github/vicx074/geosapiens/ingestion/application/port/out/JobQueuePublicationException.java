package com.github.vicx074.geosapiens.ingestion.application.port.out;

public final class JobQueuePublicationException extends RuntimeException {

  public JobQueuePublicationException(String message) {
    super(message);
  }

  public JobQueuePublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
