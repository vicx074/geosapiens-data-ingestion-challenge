package io.github.vicx074.geosapiens.ingestion.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.messaging")
public record IngestionMessagingProperties(
    String ingestionExchange,
    String ingestionQueue,
    String ingestionRoutingKey) {

  public IngestionMessagingProperties {
    ingestionExchange = requireText(ingestionExchange, "O exchange de ingestão é obrigatório.");
    ingestionQueue = requireText(ingestionQueue, "A fila de ingestão é obrigatória.");
    ingestionRoutingKey = requireText(ingestionRoutingKey, "A routing key é obrigatória.");
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.strip();
  }
}
