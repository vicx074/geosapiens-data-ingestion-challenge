package io.github.vicx074.geosapiens.ingestion.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class IngestionWorkerMetrics {

  private static final Logger LOGGER = LoggerFactory.getLogger(IngestionWorkerMetrics.class);

  public enum Outcome {
    ACK("ack"),
    REDELIVERY("redelivery"),
    DEAD_LETTER("dead_letter");

    private final String tagValue;

    Outcome(String tagValue) {
      this.tagValue = tagValue;
    }
  }

  private final MeterRegistry registry;

  public IngestionWorkerMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "O registro de métricas é obrigatório.");
  }

  /**
   * Registra telemetria como efeito auxiliar. Uma falha no backend de métricas nunca pode alterar
   * ACK, redelivery, DLQ ou estado persistido da importação.
   */
  public void record(Outcome outcome, long durationNanos) {
    Objects.requireNonNull(outcome, "O resultado da entrega é obrigatório.");
    if (durationNanos < 0) {
      throw new IllegalArgumentException("A duração da entrega não pode ser negativa.");
    }

    try {
      recordMeters(outcome, durationNanos);
    } catch (RuntimeException exception) {
      LOGGER.atWarn()
          .addKeyValue("outcome", outcome.tagValue)
          .setCause(exception)
          .log("Falha ao registrar métricas do Worker; o fluxo de processamento não será alterado.");
    }
  }

  private void recordMeters(Outcome outcome, long durationNanos) {
    String tagValue = outcome.tagValue;

    Timer.builder("ingestion.worker.delivery.duration")
        .description("Duração de uma entrega processada pelo Worker de ingestão.")
        .tag("outcome", tagValue)
        .register(registry)
        .record(durationNanos, TimeUnit.NANOSECONDS);

    Counter.builder("ingestion.worker.deliveries")
        .description("Quantidade de entregas consumidas pelo Worker de ingestão.")
        .tag("outcome", tagValue)
        .register(registry)
        .increment();
  }
}
