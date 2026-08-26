package io.github.vicx074.geosapiens.ingestion.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record IngestionTransactionRecord(
    long id,
    long sourceRow,
    String transactionId,
    Instant occurredAt,
    BigDecimal amount,
    String category) {

  public IngestionTransactionRecord {
    if (id < 1) {
      throw new IllegalArgumentException("O identificador persistido da transação deve ser positivo.");
    }
    if (sourceRow < 2) {
      throw new IllegalArgumentException(
          "A linha de origem da transação deve considerar o cabeçalho do CSV.");
    }
    transactionId = requireText(transactionId, "O identificador da transação é obrigatório.");
    occurredAt = Objects.requireNonNull(occurredAt, "A data da transação é obrigatória.");
    amount = Objects.requireNonNull(amount, "O valor da transação é obrigatório.");
    if (amount.signum() == 0) {
      throw new IllegalArgumentException("O valor da transação não pode ser zero.");
    }
    category = requireText(category, "A categoria da transação é obrigatória.");
  }

  private static String requireText(String value, String message) {
    String text = Objects.requireNonNull(value, message).trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return text;
  }
}
