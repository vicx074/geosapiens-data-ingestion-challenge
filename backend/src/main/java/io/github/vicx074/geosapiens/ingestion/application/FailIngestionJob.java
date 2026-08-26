package io.github.vicx074.geosapiens.ingestion.application;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class FailIngestionJob {

  private static final int MAX_REASON_LENGTH = 1_000;

  private final IngestionJobRepository jobs;
  private final TransactionRunner transactions;
  private final Clock clock;

  public FailIngestionJob(
      IngestionJobRepository jobs,
      TransactionRunner transactions,
      Clock clock) {
    this.jobs = jobs;
    this.transactions = transactions;
    this.clock = clock;
  }

  public IngestionJob execute(UUID jobId, String reason) {
    return transactions.required(() -> {
      VersionedIngestionJob versionedJob = findJob(jobId);
      IngestionJob job = versionedJob.job();
      if (job.getStatus().isTerminal()) {
        return job;
      }

      job.fail(boundedReason(reason), clock.instant());
      return jobs.update(versionedJob).job();
    });
  }

  private VersionedIngestionJob findJob(UUID jobId) {
    return jobs.findById(jobId).orElseThrow(() -> new IngestionJobNotFoundException(jobId));
  }

  private static String boundedReason(String reason) {
    String normalized = reason == null || reason.isBlank()
        ? "Falha sem detalhe durante o processamento."
        : reason.strip();
    return normalized.length() <= MAX_REASON_LENGTH
        ? normalized
        : normalized.substring(0, MAX_REASON_LENGTH);
  }
}
