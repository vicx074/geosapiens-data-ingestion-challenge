package io.github.vicx074.geosapiens.ingestion.infrastructure.config;

import io.github.vicx074.geosapiens.ingestion.application.OutboxPublicationPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.outbox")
public record OutboxPublisherProperties(
    boolean enabled,
    Duration pollInterval,
    int batchSize,
    Duration claimDuration,
    Duration confirmTimeout,
    int maxAttempts,
    Duration initialRetryDelay,
    Duration maxRetryDelay) {

  public OutboxPublisherProperties {
    if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
      throw new IllegalArgumentException("O intervalo de polling do Outbox deve ser positivo.");
    }
  }

  public OutboxPublicationPolicy publicationPolicy() {
    return new OutboxPublicationPolicy(
        batchSize,
        claimDuration,
        confirmTimeout,
        maxAttempts,
        initialRetryDelay,
        maxRetryDelay);
  }
}
