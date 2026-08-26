package io.github.vicx074.geosapiens.ingestion.application;

import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowConsumer;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowError;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvTransactionRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Mantém apenas um lote limitado de linhas classificadas antes de entregá-lo à fronteira transacional.
 */
final class BufferedIngestionBatchConsumer implements CsvRowConsumer {

  private final UUID jobId;
  private final int batchSize;
  private final PersistIngestionBatch persistBatch;
  private final List<CsvTransactionRow> acceptedRows;
  private final List<CsvRowError> rejectedRows;
  private int bufferedRows;

  BufferedIngestionBatchConsumer(
      UUID jobId,
      IngestionBatchPolicy policy,
      PersistIngestionBatch persistBatch) {
    this.jobId = Objects.requireNonNull(jobId, "O identificador do job é obrigatório.");
    this.batchSize = Objects.requireNonNull(policy, "A política de lote é obrigatória.").batchSize();
    this.persistBatch = Objects.requireNonNull(
        persistBatch,
        "O caso de uso de persistência do lote é obrigatório.");
    this.acceptedRows = new ArrayList<>(batchSize);
    this.rejectedRows = new ArrayList<>(batchSize);
  }

  @Override
  public void accepted(CsvTransactionRow row) {
    acceptedRows.add(Objects.requireNonNull(row, "A linha aceita é obrigatória."));
    bufferedRows++;
    flushIfFull();
  }

  @Override
  public void rejected(CsvRowError error) {
    rejectedRows.add(Objects.requireNonNull(error, "O erro de linha é obrigatório."));
    bufferedRows++;
    flushIfFull();
  }

  void flush() {
    if (bufferedRows == 0) {
      return;
    }

    persistBatch.execute(jobId, List.copyOf(acceptedRows), List.copyOf(rejectedRows));
    acceptedRows.clear();
    rejectedRows.clear();
    bufferedRows = 0;
  }

  private void flushIfFull() {
    if (bufferedRows >= batchSize) {
      flush();
    }
  }
}
