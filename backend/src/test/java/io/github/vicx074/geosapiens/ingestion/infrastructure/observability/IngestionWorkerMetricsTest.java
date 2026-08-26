package io.github.vicx074.geosapiens.ingestion.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vicx074.geosapiens.ingestion.infrastructure.observability.IngestionWorkerMetrics.Outcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IngestionWorkerMetricsTest {

  @Test
  void shouldRecordLowCardinalityOutcomeAndDuration() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    IngestionWorkerMetrics metrics = new IngestionWorkerMetrics(registry);

    metrics.record(Outcome.REDELIVERY, Duration.ofMillis(25).toNanos());

    assertThat(registry.get("ingestion.worker.deliveries")
        .tag("outcome", "redelivery")
        .counter()
        .count()).isEqualTo(1.0);

    assertThat(registry.get("ingestion.worker.delivery.duration")
        .tag("outcome", "redelivery")
        .timer()
        .count()).isEqualTo(1L);
    assertThat(registry.get("ingestion.worker.delivery.duration")
        .tag("outcome", "redelivery")
        .timer()
        .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(25.0);
  }
}
