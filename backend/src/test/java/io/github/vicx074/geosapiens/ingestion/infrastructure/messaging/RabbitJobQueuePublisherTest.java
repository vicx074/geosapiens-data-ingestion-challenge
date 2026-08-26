package io.github.vicx074.geosapiens.ingestion.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import io.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublicationException;
import io.github.vicx074.geosapiens.ingestion.infrastructure.config.IngestionMessagingProperties;
import io.github.vicx074.geosapiens.ingestion.infrastructure.config.OutboxPublisherProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitJobQueuePublisherTest {

  private static final UUID JOB_ID = UUID.fromString("cbb9f567-3ae5-4f41-87b0-221a2f436eca");

  @Mock
  private RabbitTemplate rabbitTemplate;

  @Test
  void shouldAcceptPositivePublisherConfirmation() {
    completeConfirmation(true, null);

    publisher().publish(JOB_ID);
  }

  @Test
  void shouldRejectNegativePublisherConfirmation() {
    completeConfirmation(false, "exchange indisponível");

    assertThatExceptionOfType(JobQueuePublicationException.class)
        .isThrownBy(() -> publisher().publish(JOB_ID))
        .withMessageContaining("recusou a publicação");
  }

  private void completeConfirmation(boolean acknowledged, String reason) {
    doAnswer(invocation -> {
      CorrelationData correlation = invocation.getArgument(4);
      correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, reason));
      return null;
    }).when(rabbitTemplate).convertAndSend(
        eq("ingestion.jobs"),
        eq("ingestion.process"),
        eq(JOB_ID.toString()),
        any(MessagePostProcessor.class),
        any(CorrelationData.class));
  }

  private RabbitJobQueuePublisher publisher() {
    IngestionMessagingProperties messaging = new IngestionMessagingProperties(
        "ingestion.jobs",
        "ingestion.jobs.process",
        "ingestion.process",
        "ingestion.jobs.dlx",
        "ingestion.jobs.dead",
        "ingestion.dead");
    OutboxPublisherProperties outbox = new OutboxPublisherProperties(
        true,
        Duration.ofSeconds(1),
        10,
        Duration.ofSeconds(51),
        Duration.ofSeconds(5),
        3,
        Duration.ofSeconds(1),
        Duration.ofSeconds(5));
    return new RabbitJobQueuePublisher(rabbitTemplate, messaging, outbox);
  }
}
