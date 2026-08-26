package io.github.vicx074.geosapiens.ingestion.application.port.out;

public record CsvRowError(long sourceRow, String code, String reason) {

  public CsvRowError {
    if (sourceRow < 2) {
      throw new IllegalArgumentException("A linha de origem deve apontar para uma linha de dados.");
    }
    code = requireText(code, "O código do erro é obrigatório.");
    reason = requireText(reason, "O motivo do erro é obrigatório.");
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.strip();
  }
}
