package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.util.List;
import java.util.UUID;

public interface IngestionRecordRepository {

  BatchInsertResult insertBatch(
      UUID importId,
      List<CsvTransactionRow> acceptedRows,
      List<CsvRowError> rejectedRows);
}
