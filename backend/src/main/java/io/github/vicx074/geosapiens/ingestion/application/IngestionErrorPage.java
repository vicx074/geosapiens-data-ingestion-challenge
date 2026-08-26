package io.github.vicx074.geosapiens.ingestion.application;

import java.util.List;
import java.util.Objects;

public record IngestionErrorPage(List<IngestionErrorRecord> items, Long nextCursor) {

  public IngestionErrorPage {
    items = List.copyOf(Objects.requireNonNull(items, "Os itens da página são obrigatórios."));
    if (nextCursor != null && nextCursor < 2) {
      throw new IllegalArgumentException("O próximo cursor deve apontar para uma linha válida do CSV.");
    }
  }
}
