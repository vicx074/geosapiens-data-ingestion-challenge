package com.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.vicx074.geosapiens.ingestion.application.port.out.ClaimedJobPublication;
import com.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobPublicationOutbox;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublicationException;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublisher;
import com.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
import com.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishPendingIngestionJobsTest {

  private static final UUID JOB_ID = UUID.fromString("cbb9f567-3ae5-4f41-87b0-221a2f436eca");
  private static final UUID CLAIM_TOKEN =
      UUID.fromString("ad455f67-ea8d-4422-b272-649a04de3215");
  private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

  @Mock
  private JobPublicationOutbox outbox;

  @Mock
  private JobQueuePublisher publisher;

  @Mock
  private IngestionJobRepository jobs;

  private PublishPendingIngestionJobs useCase;

  @BeforeEach
  void setUp() {
    TransactionRunner transactions = new TransactionRunner() {
      @Override
      public <T> T required(Supplier<T> action) {
        return action.get();
      }
    };
    OutboxPublicationPolicy policy = new OutboxPublicationPolicy(
        10,
        Duration.ofSeconds(51),
        Duration.ofSeconds(5),
        3,
        Duration.ofSeconds(1),
        Duration.ofSeconds(5));
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    useCase = new PublishPendingIngestionJobs(
        outbox, publisher, jobs, transactions, policy, clock);
  }

  @Test
  void shouldPublishClaimedJob() {
    ClaimedJobPublication publication = new ClaimedJobPublication(JOB_ID, 1, CLAIM_TOKEN);
    when(outbox.claimAvailable(any(), any(), eq(10), any())).thenReturn(List.of(publication));

    int published = useCase.execute();

    assertThat(published).isEqualTo(1);
    verify(publisher).publish(JOB_ID);
    verify(outbox).markPublished(JOB_ID, CLAIM_TOKEN, NOW);
    verifyNoInteractions(jobs);
  }

  @Test
  void shouldRescheduleFailedPublicationBeforeAttemptLimit() {
    ClaimedJobPublication publication = new ClaimedJobPublication(JOB_ID, 1, CLAIM_TOKEN);
    when(outbox.claimAvailable(any(), any(), eq(10), any())).thenReturn(List.of(publication));
    doThrow(new JobQueuePublicationException("RabbitMQ indisponível"))
        .when(publisher).publish(JOB_ID);

    useCase.execute();

    verify(outbox).reschedule(
        JOB_ID, CLAIM_TOKEN, NOW.plusSeconds(1), "RabbitMQ indisponível");
  }

  @Test
  void shouldFailJobWhenPublicationAttemptsAreExhausted() {
    ClaimedJobPublication publication = new ClaimedJobPublication(JOB_ID, 3, CLAIM_TOKEN);
    IngestionJob job = IngestionJob.receive(JOB_ID, "transactions.csv", NOW.minusSeconds(10));
    job.markQueued(NOW.minusSeconds(9));
    VersionedIngestionJob versionedJob = new VersionedIngestionJob(job, 0);
    when(outbox.claimAvailable(any(), any(), eq(10), any())).thenReturn(List.of(publication));
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(versionedJob));
    doThrow(new JobQueuePublicationException("RabbitMQ indisponível"))
        .when(publisher).publish(JOB_ID);

    useCase.execute();

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.FAILED);
    verify(jobs).update(versionedJob);
    verify(outbox).markFailed(JOB_ID, CLAIM_TOKEN, NOW, "RabbitMQ indisponível");
  }
}
