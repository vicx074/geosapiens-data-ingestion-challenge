package io.github.vicx074.geosapiens.ingestion.application.port.out;

import io.github.vicx074.geosapiens.ingestion.application.IngestionErrorRecord;
import java.util.List;
import java.util.UUID;

public interface IngestionErrorQuery {

  /**
   * Busca erros depois da linha informada, em ordem crescente de linha de origem, com resultado
   * limitado pelo chamador. A implementação não deve recorrer a OFFSET profundo.
   */
  List<IngestionErrorRecord> findAfter(UUID importId, long afterSourceRow, int maxRows);
}
