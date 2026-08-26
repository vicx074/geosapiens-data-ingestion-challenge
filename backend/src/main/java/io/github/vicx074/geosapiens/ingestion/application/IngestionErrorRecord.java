package io.github.vicx074.geosapiens.ingestion.application;

import java.util.Objects;

public record IngestionErrorRecord(long sourceRow, String code, String reason) {

  public IngestionErrorRecord {
    if (sourceRow < 2) {
      throw new IllegalArgumentException("A linha de origem do erro deve considerar o cabeçalho do CSV.");
    }
    code = requireText(code, "O código do erro é obrigatório.");
    reason = requireText(reason, "O motivo do erro é obrigatório.");
  }

  private static String requireText(String value, String message) {
    String text = Objects.requireNonNull(value, message).trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return text;
  }
}
