package com.github.vicx074.geosapiens.ingestion.application;

import com.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobPublicationOutbox;
import com.github.vicx074.geosapiens.ingestion.application.port.out.PendingJobPublication;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import com.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class RegisterIngestionJob {

  private final IngestionJobRepository jobs;
  private final JobPublicationOutbox outbox;
  private final TransactionRunner transactions;

  public RegisterIngestionJob(
      IngestionJobRepository jobs,
      JobPublicationOutbox outbox,
      TransactionRunner transactions) {
    this.jobs = jobs;
    this.outbox = outbox;
    this.transactions = transactions;
  }

  public IngestionJob execute(UUID jobId, String originalFilename, Instant receivedAt) {
    IngestionJob job = IngestionJob.receive(jobId, originalFilename, receivedAt);
    job.markQueued(receivedAt);
    PendingJobPublication publication = new PendingJobPublication(jobId, receivedAt);

    return transactions.required(() -> {
      jobs.insert(job);
      outbox.insert(publication);
      return job;
    });
  }
}
