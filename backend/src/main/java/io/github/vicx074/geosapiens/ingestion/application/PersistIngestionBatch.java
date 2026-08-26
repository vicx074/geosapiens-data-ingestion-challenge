package io.github.vicx074.geosapiens.ingestion.application;

import io.github.vicx074.geosapiens.ingestion.application.port.out.BatchInsertResult;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowError;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvTransactionRow;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionRecordRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
import io.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class PersistIngestionBatch {

  private final IngestionRecordRepository records;
  private final IngestionJobRepository jobs;
  private final TransactionRunner transactions;
  private final Clock clock;

  public PersistIngestionBatch(
      IngestionRecordRepository records,
      IngestionJobRepository jobs,
      TransactionRunner transactions,
      Clock clock) {
    this.records = records;
    this.jobs = jobs;
    this.transactions = transactions;
    this.clock = clock;
  }

  public BatchInsertResult execute(
      UUID jobId,
      List<CsvTransactionRow> acceptedRows,
      List<CsvRowError> rejectedRows) {
    Objects.requireNonNull(jobId, "O identificador do job é obrigatório.");
    List<CsvTransactionRow> accepted = List.copyOf(
        Objects.requireNonNull(acceptedRows, "As linhas aceitas são obrigatórias."));
    List<CsvRowError> rejected = List.copyOf(
        Objects.requireNonNull(rejectedRows, "As linhas rejeitadas são obrigatórias."));
    if (accepted.isEmpty() && rejected.isEmpty()) {
      throw new IllegalArgumentException("O lote deve conter ao menos uma linha.");
    }

    return transactions.required(() -> {
      VersionedIngestionJob versionedJob = findJob(jobId);
      IngestionJob job = versionedJob.job();

      // Uma publicação duplicada pode alcançar outro consumer depois que o job já terminou.
      if (job.getStatus().isTerminal()) {
        return new BatchInsertResult(0, 0);
      }
      if (job.getStatus() != IngestionStatus.PROCESSING) {
        throw new IllegalStateException(
            "Somente jobs em PROCESSING podem persistir lotes de ingestão.");
      }

      BatchInsertResult inserted = records.insertBatch(jobId, accepted, rejected);
      if (inserted.processedRows() > 0) {
        job.recordBatch(inserted.acceptedRows(), inserted.rejectedRows(), clock.instant());
        jobs.update(versionedJob);
      }
      return inserted;
    });
  }

  private VersionedIngestionJob findJob(UUID jobId) {
    return jobs.findById(jobId).orElseThrow(() -> new IngestionJobNotFoundException(jobId));
  }
}
