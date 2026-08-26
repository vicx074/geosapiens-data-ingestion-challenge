package io.github.vicx074.geosapiens.ingestion.infrastructure.messaging;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rabbitmq.client.Channel;
import io.github.vicx074.geosapiens.ingestion.application.FailIngestionJob;
import io.github.vicx074.geosapiens.ingestion.application.ProcessIngestionJob;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RabbitIngestionJobListenerTest {

  private static final UUID JOB_ID = UUID.fromString("b50f275d-29f2-4efd-96fd-158035649598");
  private static final long DELIVERY_TAG = 42L;

  private final ProcessIngestionJob processJob = mock(ProcessIngestionJob.class);
  private final FailIngestionJob failJob = mock(FailIngestionJob.class);
  private final Channel channel = mock(Channel.class);
  private final RabbitIngestionJobListener listener =
      new RabbitIngestionJobListener(processJob, failJob);

  @Test
  void shouldAckOnlyAfterSuccessfulProcessing() throws Exception {
    listener.consume(JOB_ID.toString(), message(false), channel);

    verify(processJob).execute(JOB_ID);
    verify(channel).basicAck(DELIVERY_TAG, false);
    verifyNoInteractions(failJob);
  }

  @Test
  void shouldRequestOneRedeliveryAfterFirstFailure() throws Exception {
    doThrow(new IllegalStateException("falha transitória")).when(processJob).execute(JOB_ID);

    listener.consume(JOB_ID.toString(), message(false), channel);

    verify(channel).basicNack(DELIVERY_TAG, false, true);
    verifyNoInteractions(failJob);
  }

  @Test
  void shouldFailJobAndDeadLetterAfterRepeatedFailure() throws Exception {
    doThrow(new IllegalStateException("falha repetida")).when(processJob).execute(JOB_ID);

    listener.consume(JOB_ID.toString(), message(true), channel);

    verify(failJob).execute(JOB_ID, "falha repetida");
    verify(channel).basicReject(DELIVERY_TAG, false);
  }

  @Test
  void shouldDeadLetterMalformedJobIdentifierWithoutProcessing() throws Exception {
    listener.consume("not-a-uuid", message(false), channel);

    verify(channel).basicReject(DELIVERY_TAG, false);
    verifyNoInteractions(processJob, failJob);
  }

  private static Message message(boolean redelivered) {
    MessageProperties properties = new MessageProperties();
    properties.setDeliveryTag(DELIVERY_TAG);
    properties.setRedelivered(redelivered);
    return new Message(new byte[0], properties);
  }
}
