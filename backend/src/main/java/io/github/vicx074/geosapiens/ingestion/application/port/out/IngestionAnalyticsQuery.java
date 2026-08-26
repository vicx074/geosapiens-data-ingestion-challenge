package io.github.vicx074.geosapiens.ingestion.application.port.out;

import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics;
import java.util.UUID;

public interface IngestionAnalyticsQuery {

  /**
   * Calcula os indicadores de uma importação em um único snapshot de leitura. A implementação deve
   * delegar as agregações ao banco, sem carregar as transações para somá-las na aplicação.
   */
  IngestionAnalytics fetch(UUID importId);
}
