package com.github.vicx074.geosapiens.ingestion.application;

import com.github.vicx074.geosapiens.ingestion.application.port.out.ClaimedJobPublication;
import com.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobPublicationOutbox;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublicationException;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublisher;
import com.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import com.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PublishPendingIngestionJobs {

  private static final int MAX_ERROR_LENGTH = 1_000;

  private final JobPublicationOutbox outbox;
  private final JobQueuePublisher publisher;
  private final IngestionJobRepository jobs;
  private final TransactionRunner transactions;
  private final OutboxPublicationPolicy policy;
  private final Clock clock;

  public PublishPendingIngestionJobs(
      JobPublicationOutbox outbox,
      JobQueuePublisher publisher,
      IngestionJobRepository jobs,
      TransactionRunner transactions,
      OutboxPublicationPolicy policy,
      Clock clock) {
    this.outbox = outbox;
    this.publisher = publisher;
    this.jobs = jobs;
    this.transactions = transactions;
    this.policy = policy;
    this.clock = clock;
  }

  public int execute() {
    Instant claimedAt = clock.instant();
    List<ClaimedJobPublication> publications = outbox.claimAvailable(
        claimedAt,
        claimedAt.plus(policy.claimDuration()),
        policy.batchSize(),
        UUID.randomUUID());

    publications.forEach(this::publish);
    return publications.size();
  }

  private void publish(ClaimedJobPublication publication) {
    try {
      publisher.publish(publication.jobId());
    } catch (JobQueuePublicationException exception) {
      handleFailure(publication, exception);
      return;
    }

    Instant publishedAt = clock.instant();
    outbox.markPublished(publication.jobId(), publication.claimToken(), publishedAt);
  }

  private void handleFailure(
      ClaimedJobPublication publication, JobQueuePublicationException exception) {
    Instant failedAt = clock.instant();
    String error = boundedError(exception);

    if (publication.attempts() < policy.maxAttempts()) {
      Instant availableAt = failedAt.plus(policy.retryDelay(publication.attempts()));
      outbox.reschedule(publication.jobId(), publication.claimToken(), availableAt, error);
      return;
    }

    transactions.required(() -> {
      VersionedIngestionJob versionedJob = findJob(publication.jobId());
      versionedJob.job().fail(error, failedAt);
      jobs.update(versionedJob);
      outbox.markFailed(publication.jobId(), publication.claimToken(), failedAt, error);
      return null;
    });
  }

  private VersionedIngestionJob findJob(UUID jobId) {
    return jobs.findById(jobId).orElseThrow(() -> new IngestionJobNotFoundException(jobId));
  }

  private static String boundedError(JobQueuePublicationException exception) {
    String message = exception.getMessage();
    String error = message == null || message.isBlank()
        ? "Falha sem detalhe ao publicar no RabbitMQ."
        : message.strip();
    return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
  }
}
