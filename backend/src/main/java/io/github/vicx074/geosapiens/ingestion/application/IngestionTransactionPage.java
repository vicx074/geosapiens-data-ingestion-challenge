package io.github.vicx074.geosapiens.ingestion.application;

import java.util.List;
import java.util.Objects;

public record IngestionTransactionPage(
    List<IngestionTransactionRecord> items,
    Long nextCursor) {

  public IngestionTransactionPage {
    items = List.copyOf(Objects.requireNonNull(items, "Os itens da página são obrigatórios."));
    if (nextCursor != null && nextCursor < 1) {
      throw new IllegalArgumentException("O próximo cursor deve apontar para uma transação válida.");
    }
  }
}
