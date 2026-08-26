package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record CsvTransactionRow(
    long sourceRow,
    String transactionId,
    Instant occurredAt,
    BigDecimal amount,
    String category) {

  public CsvTransactionRow {
    if (sourceRow < 2) {
      throw new IllegalArgumentException("A linha de origem deve apontar para uma linha de dados.");
    }
    Objects.requireNonNull(transactionId, "O identificador da transação é obrigatório.");
    Objects.requireNonNull(occurredAt, "A data da transação é obrigatória.");
    Objects.requireNonNull(amount, "O valor da transação é obrigatório.");
    Objects.requireNonNull(category, "A categoria é obrigatória.");
  }
}
