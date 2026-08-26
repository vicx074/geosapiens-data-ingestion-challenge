package io.github.vicx074.geosapiens.ingestion.application;

import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvProcessingSummary;
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
  private final PersistIngestionBatch persistBatch;
  private final IngestionBatchPolicy batchPolicy;
  private final FailIngestionJob failJob;
  private final TransactionRunner transactions;
  private final Clock clock;

  public ProcessIngestionJob(
      IngestionJobRepository jobs,
      TemporaryFileStorage storage,
      IngestionCsvProcessor csvProcessor,
      PersistIngestionBatch persistBatch,
      IngestionBatchPolicy batchPolicy,
      FailIngestionJob failJob,
      TransactionRunner transactions,
      Clock clock) {
    this.jobs = jobs;
    this.storage = storage;
    this.csvProcessor = csvProcessor;
    this.persistBatch = persistBatch;
    this.batchPolicy = batchPolicy;
    this.failJob = failJob;
    this.transactions = transactions;
    this.clock = clock;
  }

  public IngestionJob execute(UUID jobId) {
    IngestionJob startedJob = startProcessing(jobId);
    if (startedJob.getStatus().isTerminal()) {
      cleanupTemporaryFile(jobId);
      return startedJob;
    }

    BufferedIngestionBatchConsumer rowConsumer =
        new BufferedIngestionBatchConsumer(jobId, batchPolicy, persistBatch);
    CsvProcessingSummary summary;
    try (InputStream content = storage.open(jobId)) {
      summary = csvProcessor.process(content, rowConsumer);
      rowConsumer.flush();
    } catch (InvalidCsvFileException exception) {
      IngestionJob failedJob = failJob.execute(jobId, exception.getMessage());
      cleanupTemporaryFile(jobId);
      return failedJob;
    } catch (IOException exception) {
      throw new IngestionProcessingException(jobId, exception);
    }

    IngestionJob completedJob = complete(jobId, summary);
    cleanupTemporaryFile(jobId);
    return completedJob;
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
        throw new IllegalStateException("O job deve estar em PROCESSING antes da conclusão.");
      }
      if (job.getAcceptedRows() != summary.acceptedRows()
          || job.getRejectedRows() != summary.rejectedRows()) {
        throw new IllegalStateException(
            "Os contadores persistidos não correspondem às linhas percorridas no CSV.");
      }

      job.complete(clock.instant());
      return jobs.update(versionedJob).job();
    });
  }

  private void cleanupTemporaryFile(UUID jobId) {
    try {
      storage.delete(jobId);
    } catch (IOException exception) {
      throw new IngestionProcessingException(jobId, exception);
    }
  }

  private VersionedIngestionJob findJob(UUID jobId) {
    return jobs.findById(jobId).orElseThrow(() -> new IngestionJobNotFoundException(jobId));
  }
}
