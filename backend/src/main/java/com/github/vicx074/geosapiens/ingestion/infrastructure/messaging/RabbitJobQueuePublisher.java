package com.github.vicx074.geosapiens.ingestion.infrastructure.messaging;

import com.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublicationException;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublisher;
import com.github.vicx074.geosapiens.ingestion.infrastructure.config.IngestionMessagingProperties;
import com.github.vicx074.geosapiens.ingestion.infrastructure.config.OutboxPublisherProperties;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true")
public final class RabbitJobQueuePublisher implements JobQueuePublisher {

  private final RabbitTemplate rabbitTemplate;
  private final IngestionMessagingProperties messaging;
  private final Duration confirmTimeout;

  public RabbitJobQueuePublisher(
      RabbitTemplate rabbitTemplate,
      IngestionMessagingProperties messaging,
      OutboxPublisherProperties outbox) {
    this.rabbitTemplate = rabbitTemplate;
    this.messaging = messaging;
    this.confirmTimeout = outbox.confirmTimeout();
  }

  @Override
  public void publish(UUID jobId) {
    CorrelationData correlation = new CorrelationData(jobId.toString());

    try {
      rabbitTemplate.convertAndSend(
          messaging.ingestionExchange(),
          messaging.ingestionRoutingKey(),
          jobId.toString(),
          message -> {
            message.getMessageProperties().setMessageId(jobId.toString());
            message.getMessageProperties().setType("ingestion.job.requested");
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
          },
          correlation);

      CorrelationData.Confirm confirm = correlation.getFuture()
          .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);

      if (correlation.getReturned() != null) {
        throw new JobQueuePublicationException(
            "O RabbitMQ devolveu a mensagem por ausência de rota para o job %s."
                .formatted(jobId));
      }
      if (!confirm.ack()) {
        throw new JobQueuePublicationException(
            "O RabbitMQ recusou a publicação do job %s: %s"
                .formatted(jobId, confirm.reason()));
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new JobQueuePublicationException(
          "A confirmação da publicação do job %s foi interrompida.".formatted(jobId),
          exception);
    } catch (AmqpException | ExecutionException | TimeoutException exception) {
      throw new JobQueuePublicationException(
          "Não foi possível confirmar a publicação do job %s.".formatted(jobId),
          exception);
    }
  }
}
