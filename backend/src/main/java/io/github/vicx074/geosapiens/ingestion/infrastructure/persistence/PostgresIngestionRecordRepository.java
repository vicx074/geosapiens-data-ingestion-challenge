package io.github.vicx074.geosapiens.ingestion.infrastructure.persistence;

import io.github.vicx074.geosapiens.ingestion.application.port.out.BatchInsertResult;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowError;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvTransactionRow;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionRecordRepository;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresIngestionRecordRepository implements IngestionRecordRepository {

  private static final String INSERT_TRANSACTION = """
      INSERT INTO transactions (
          import_id,
          source_row,
          transaction_id,
          occurred_at,
          amount,
          category
      ) VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT (import_id, source_row) DO NOTHING
      """;

  private static final String INSERT_ERROR = """
      INSERT INTO ingestion_errors (
          import_id,
          source_row,
          error_code,
          reason
      ) VALUES (?, ?, ?, ?)
      ON CONFLICT (import_id, source_row) DO NOTHING
      """;

  private static final int[] TRANSACTION_TYPES = {
      Types.OTHER,
      Types.BIGINT,
      Types.VARCHAR,
      Types.TIMESTAMP_WITH_TIMEZONE,
      Types.NUMERIC,
      Types.VARCHAR
  };

  private static final int[] ERROR_TYPES = {
      Types.OTHER,
      Types.BIGINT,
      Types.VARCHAR,
      Types.VARCHAR
  };

  private final JdbcTemplate jdbcTemplate;

  public PostgresIngestionRecordRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public BatchInsertResult insertBatch(
      UUID importId,
      List<CsvTransactionRow> acceptedRows,
      List<CsvRowError> rejectedRows) {
    Objects.requireNonNull(importId, "O identificador da importação é obrigatório.");
    Objects.requireNonNull(acceptedRows, "As linhas aceitas são obrigatórias.");
    Objects.requireNonNull(rejectedRows, "As linhas rejeitadas são obrigatórias.");

    long accepted = insertTransactions(importId, acceptedRows);
    long rejected = insertErrors(importId, rejectedRows);
    return new BatchInsertResult(accepted, rejected);
  }

  private long insertTransactions(UUID importId, List<CsvTransactionRow> rows) {
    if (rows.isEmpty()) {
      return 0;
    }

    List<Object[]> parameters = new ArrayList<>(rows.size());
    for (CsvTransactionRow row : rows) {
      parameters.add(new Object[] {
          importId,
          row.sourceRow(),
          row.transactionId(),
          OffsetDateTime.ofInstant(row.occurredAt(), ZoneOffset.UTC),
          row.amount(),
          row.category()
      });
    }
    return countInserted(jdbcTemplate.batchUpdate(INSERT_TRANSACTION, parameters, TRANSACTION_TYPES));
  }

  private long insertErrors(UUID importId, List<CsvRowError> rows) {
    if (rows.isEmpty()) {
      return 0;
    }

    List<Object[]> parameters = new ArrayList<>(rows.size());
    for (CsvRowError row : rows) {
      parameters.add(new Object[] {
          importId,
          row.sourceRow(),
          row.code(),
          row.reason()
      });
    }
    return countInserted(jdbcTemplate.batchUpdate(INSERT_ERROR, parameters, ERROR_TYPES));
  }

  private static long countInserted(int[] updateCounts) {
    long inserted = 0;
    for (int updateCount : updateCounts) {
      if (updateCount == Statement.EXECUTE_FAILED) {
        throw new IllegalStateException("O PostgreSQL informou falha em uma operação do batch.");
      }
      if (updateCount == Statement.SUCCESS_NO_INFO) {
        throw new IllegalStateException(
            "O driver não informou quantas linhas do batch foram inseridas; "
                + "a idempotência do progresso exige essa contagem.");
      }
      if (updateCount < 0) {
        throw new IllegalStateException("O driver retornou uma contagem de batch inválida.");
      }
      inserted = Math.addExact(inserted, updateCount);
    }
    return inserted;
  }
}
