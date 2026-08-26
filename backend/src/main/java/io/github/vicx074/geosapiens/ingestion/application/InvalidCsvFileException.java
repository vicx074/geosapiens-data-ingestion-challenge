package io.github.vicx074.geosapiens.ingestion.application;

public class InvalidCsvFileException extends RuntimeException {

  public InvalidCsvFileException(String message) {
    super(message);
  }

  public InvalidCsvFileException(String message, Throwable cause) {
    super(message, cause);
  }
}
