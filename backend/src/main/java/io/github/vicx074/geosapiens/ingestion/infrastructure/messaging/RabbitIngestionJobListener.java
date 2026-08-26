package io.github.vicx074.geosapiens.ingestion.infrastructure.messaging;

import com.rabbitmq.client.Channel;
import io.github.vicx074.geosapiens.ingestion.application.FailIngestionJob;
import io.github.vicx074.geosapiens.ingestion.application.IngestionJobNotFoundException;
import io.github.vicx074.geosapiens.ingestion.application.ProcessIngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.ingestion.infrastructure.observability.IngestionWorkerMetrics;
import io.github.vicx074.geosapiens.ingestion.infrastructure.observability.IngestionWorkerMetrics.Outcome;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public final class RabbitIngestionJobListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitIngestionJobListener.class);

  private final ProcessIngestionJob processJob;
  private final FailIngestionJob failJob;
  private final IngestionWorkerMetrics metrics;

  public RabbitIngestionJobListener(
      ProcessIngestionJob processJob,
      FailIngestionJob failJob,
      IngestionWorkerMetrics metrics) {
    this.processJob = processJob;
    this.failJob = failJob;
    this.metrics = metrics;
  }

  @RabbitListener(
      queues = "${app.messaging.ingestion-queue}",
      containerFactory = "ingestionWorkerContainerFactory",
      autoStartup = "${app.worker.enabled:false}")
  public void consume(String payload, Message message, Channel channel) throws IOException {
    long startedNanos = System.nanoTime();
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    UUID jobId;
    try {
      jobId = UUID.fromString(payload.strip());
    } catch (RuntimeException exception) {
      channel.basicReject(deliveryTag, false);
      record(Outcome.DEAD_LETTER, startedNanos);
      LOGGER.atWarn()
          .addKeyValue("action", "dead_letter")
          .log("Mensagem de ingestão inválida recebida: identificador de job ausente ou malformado.");
      return;
    }

    try {
      IngestionJob job = processJob.execute(jobId);
      channel.basicAck(deliveryTag, false);
      long durationNanos = record(Outcome.ACK, startedNanos);
      logCompleted(job, durationNanos);
    } catch (IngestionJobNotFoundException exception) {
      channel.basicReject(deliveryTag, false);
      record(Outcome.DEAD_LETTER, startedNanos);
      LOGGER.atWarn()
          .addKeyValue("jobId", jobId)
          .addKeyValue("action", "dead_letter")
          .setCause(exception)
          .log("Job não encontrado; mensagem enviada para a DLQ.");
    } catch (RuntimeException exception) {
      handleProcessingFailure(jobId, message, channel, deliveryTag, startedNanos, exception);
    }
  }

  private void handleProcessingFailure(
      UUID jobId,
      Message message,
      Channel channel,
      long deliveryTag,
      long startedNanos,
      RuntimeException exception) throws IOException {
    boolean redelivered = Boolean.TRUE.equals(message.getMessageProperties().getRedelivered());
    if (!redelivered) {
      channel.basicNack(deliveryTag, false, true);
      long durationNanos = record(Outcome.REDELIVERY, startedNanos);
      LOGGER.atWarn()
          .addKeyValue("jobId", jobId)
          .addKeyValue("action", "redelivery")
          .addKeyValue("durationMs", nanosToMillis(durationNanos))
          .setCause(exception)
          .log("Falha ao processar job; uma nova entrega foi solicitada.");
      return;
    }

    try {
      failJob.execute(jobId, failureReason(exception));
    } catch (RuntimeException failureUpdateException) {
      exception.addSuppressed(failureUpdateException);

      // O orçamento de retry já foi consumido. Reenfileirar outra vez pode criar um loop quente
      // justamente quando o PostgreSQL está indisponível; a DLQ preserva a mensagem para reconciliação.
      channel.basicReject(deliveryTag, false);
      long durationNanos = record(Outcome.DEAD_LETTER, startedNanos);
      LOGGER.atError()
          .addKeyValue("jobId", jobId)
          .addKeyValue("action", "dead_letter")
          .addKeyValue("requiresReconciliation", true)
          .addKeyValue("durationMs", nanosToMillis(durationNanos))
          .setCause(exception)
          .log("Não foi possível registrar a falha definitiva; mensagem enviada para a DLQ.");
      return;
    }

    channel.basicReject(deliveryTag, false);
    long durationNanos = record(Outcome.DEAD_LETTER, startedNanos);
    LOGGER.atError()
        .addKeyValue("jobId", jobId)
        .addKeyValue("action", "dead_letter")
        .addKeyValue("durationMs", nanosToMillis(durationNanos))
        .setCause(exception)
        .log("Job falhou novamente após redelivery; mensagem enviada para a DLQ.");
  }

  private long record(Outcome outcome, long startedNanos) {
    long durationNanos = Math.max(0, System.nanoTime() - startedNanos);
    metrics.record(outcome, durationNanos);
    return durationNanos;
  }

  private static void logCompleted(IngestionJob job, long durationNanos) {
    LOGGER.atInfo()
        .addKeyValue("jobId", job.getId())
        .addKeyValue("status", job.getStatus().name())
        .addKeyValue("processedRows", job.getProcessedRows())
        .addKeyValue("acceptedRows", job.getAcceptedRows())
        .addKeyValue("rejectedRows", job.getRejectedRows())
        .addKeyValue("durationMs", nanosToMillis(durationNanos))
        .log("Processamento de ingestão concluído.");
  }

  private static long nanosToMillis(long durationNanos) {
    return durationNanos / 1_000_000;
  }

  private static String failureReason(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank()
        ? "Falha sem detalhe durante o processamento do Worker."
        : message;
  }
}
