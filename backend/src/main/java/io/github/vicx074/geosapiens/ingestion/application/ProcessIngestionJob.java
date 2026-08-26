package io.github.vicx074.geosapiens.ingestion.application;

import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvProcessingSummary;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowConsumer;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionCsvProcessor;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.TemporaryFileStorage;
import io.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
import io.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ProcessIngestionJob {

  private final IngestionJobRepository jobs;
  private final TemporaryFileStorage storage;
  private final IngestionCsvProcessor csvProcessor;
  private final FailIngestionJob failJob;
  private final TransactionRunner transactions;
  private final Clock clock;

  public ProcessIngestionJob(
      IngestionJobRepository jobs,
      TemporaryFileStorage storage,
      IngestionCsvProcessor csvProcessor,
      FailIngestionJob failJob,
      TransactionRunner transactions,
      Clock clock) {
    this.jobs = jobs;
    this.storage = storage;
    this.csvProcessor = csvProcessor;
    this.failJob = failJob;
    this.transactions = transactions;
    this.clock = clock;
  }

  public IngestionJob execute(UUID jobId) {
    IngestionJob startedJob = startProcessing(jobId);
    if (startedJob.getStatus().isTerminal()) {
      return startedJob;
    }

    CsvProcessingSummary summary;
    try (InputStream content = storage.open(jobId)) {
      summary = csvProcessor.process(content, CsvRowConsumer.DISCARDING);
    } catch (InvalidCsvFileException exception) {
      return failJob.execute(jobId, exception.getMessage());
    } catch (IOException exception) {
      throw new IngestionProcessingException(jobId, exception);
    }

    return complete(jobId, summary);
  }

  private IngestionJob startProcessing(UUID jobId) {
    return transactions.required(() -> {
      VersionedIngestionJob versionedJob = findJob(jobId);
      IngestionJob job = versionedJob.job();
      if (job.getStatus().isTerminal() || job.getStatus() == IngestionStatus.PROCESSING) {
        return job;
      }

      job.startProcessing(clock.instant());
      return jobs.update(versionedJob).job();
    });
  }

  private IngestionJob complete(UUID jobId, CsvProcessingSummary summary) {
    return transactions.required(() -> {
      VersionedIngestionJob versionedJob = findJob(jobId);
      IngestionJob job = versionedJob.job();
      if (job.getStatus().isTerminal()) {
        return job;
      }
      if (job.getStatus() != IngestionStatus.PROCESSING) {
        job.startProcessing(clock.instant());
      }
      if (summary.processedRows() > 0) {
        job.recordBatch(summary.acceptedRows(), summary.rejectedRows(), clock.instant());
      }
      job.complete(clock.instant());
      return jobs.update(versionedJob).job();
    });
  }

  private VersionedIngestionJob findJob(UUID jobId) {
    return jobs.findById(jobId).orElseThrow(() -> new IngestionJobNotFoundException(jobId));
  }
}
