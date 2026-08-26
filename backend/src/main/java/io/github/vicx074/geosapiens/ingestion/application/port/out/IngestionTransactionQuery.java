package io.github.vicx074.geosapiens.ingestion.application.port.out;

import io.github.vicx074.geosapiens.ingestion.application.IngestionTransactionRecord;
import java.util.List;
import java.util.UUID;

public interface IngestionTransactionQuery {

  /**
   * Busca transações depois do identificador persistido informado, em ordem crescente de id e com
   * resultado limitado pelo chamador. A implementação não deve recorrer a OFFSET profundo.
   */
  List<IngestionTransactionRecord> findAfter(UUID importId, long afterId, int maxRows);
}
