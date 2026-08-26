package io.github.vicx074.geosapiens.ingestion.infrastructure.messaging;

import com.rabbitmq.client.Channel;
import io.github.vicx074.geosapiens.ingestion.application.FailIngestionJob;
import io.github.vicx074.geosapiens.ingestion.application.IngestionJobNotFoundException;
import io.github.vicx074.geosapiens.ingestion.application.ProcessIngestionJob;
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

  public RabbitIngestionJobListener(ProcessIngestionJob processJob, FailIngestionJob failJob) {
    this.processJob = processJob;
    this.failJob = failJob;
  }

  @RabbitListener(
      queues = "${app.messaging.ingestion-queue}",
      containerFactory = "ingestionWorkerContainerFactory",
      autoStartup = "${app.worker.enabled:false}")
  public void consume(String payload, Message message, Channel channel) throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    UUID jobId;
    try {
      jobId = UUID.fromString(payload.strip());
    } catch (RuntimeException exception) {
      LOGGER.warn("Mensagem de ingestão inválida recebida: identificador de job ausente ou malformado.");
      channel.basicReject(deliveryTag, false);
      return;
    }

    try {
      processJob.execute(jobId);
      channel.basicAck(deliveryTag, false);
    } catch (IngestionJobNotFoundException exception) {
      LOGGER.warn("Job {} não existe; mensagem será enviada para a DLQ.", jobId);
      channel.basicReject(deliveryTag, false);
    } catch (RuntimeException exception) {
      handleProcessingFailure(jobId, message, channel, deliveryTag, exception);
    }
  }

  private void handleProcessingFailure(
      UUID jobId,
      Message message,
      Channel channel,
      long deliveryTag,
      RuntimeException exception) throws IOException {
    boolean redelivered = Boolean.TRUE.equals(message.getMessageProperties().getRedelivered());
    if (!redelivered) {
      LOGGER.warn("Falha ao processar job {}; uma nova entrega será solicitada.", jobId, exception);
      channel.basicNack(deliveryTag, false, true);
      return;
    }

    try {
      failJob.execute(jobId, failureReason(exception));
    } catch (RuntimeException failureUpdateException) {
      exception.addSuppressed(failureUpdateException);

      // O orçamento de retry já foi consumido. Reenfileirar outra vez pode criar um loop quente
      // justamente quando o PostgreSQL está indisponível; a DLQ preserva a mensagem para reconciliação.
      LOGGER.error(
          "Não foi possível registrar a falha definitiva do job {}; mensagem será enviada para a DLQ "
              + "para evitar redelivery sem limite. O estado do job pode exigir reconciliação.",
          jobId,
          exception);
      channel.basicReject(deliveryTag, false);
      return;
    }

    LOGGER.error("Job {} falhou novamente após redelivery; mensagem será enviada para a DLQ.",
        jobId, exception);
    channel.basicReject(deliveryTag, false);
  }

  private static String failureReason(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank()
        ? "Falha sem detalhe durante o processamento do Worker."
        : message;
  }
}
