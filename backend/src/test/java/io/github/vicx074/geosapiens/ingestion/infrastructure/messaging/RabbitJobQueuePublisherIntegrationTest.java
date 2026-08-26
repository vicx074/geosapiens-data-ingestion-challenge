package io.github.vicx074.geosapiens.ingestion.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublisher;
import io.github.vicx074.geosapiens.ingestion.infrastructure.config.IngestionMessagingProperties;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
    "app.outbox.enabled=true",
    "app.outbox.poll-interval=1h"
})
class RabbitJobQueuePublisherIntegrationTest {

  private static final UUID JOB_ID = UUID.fromString("cbb9f567-3ae5-4f41-87b0-221a2f436eca");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Container
  @ServiceConnection
  static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer("rabbitmq:4.3.5-alpine");

  @Autowired
  private JobQueuePublisher publisher;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private IngestionMessagingProperties messaging;

  @Test
  void shouldPublishPersistentRoutableMessageWithConfirmation() {
    publisher.publish(JOB_ID);

    Message received = rabbitTemplate.receive(messaging.ingestionQueue(), 5_000);
    assertThat(received).isNotNull();
    assertThat(new String(received.getBody(), StandardCharsets.UTF_8))
        .isEqualTo(JOB_ID.toString());
    assertThat(received.getMessageProperties().getMessageId()).isEqualTo(JOB_ID.toString());
    assertThat(received.getMessageProperties().getReceivedDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
  }
}
