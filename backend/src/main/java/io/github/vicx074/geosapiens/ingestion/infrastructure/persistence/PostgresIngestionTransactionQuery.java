package io.github.vicx074.geosapiens.ingestion.infrastructure.persistence;

import io.github.vicx074.geosapiens.ingestion.application.IngestionTransactionRecord;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionTransactionQuery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresIngestionTransactionQuery implements IngestionTransactionQuery {

  private static final String FIND_AFTER = """
      SELECT id, source_row, transaction_id, occurred_at, amount, category
      FROM transactions
      WHERE import_id = :importId
        AND id > :afterId
      ORDER BY id
      LIMIT :maxRows
      """;

  private final JdbcClient jdbcClient;

  public PostgresIngestionTransactionQuery(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public List<IngestionTransactionRecord> findAfter(UUID importId, long afterId, int maxRows) {
    Objects.requireNonNull(importId, "O identificador da importação é obrigatório.");
    if (afterId < 0) {
      throw new IllegalArgumentException("O identificador usado como cursor não pode ser negativo.");
    }
    if (maxRows < 1) {
      throw new IllegalArgumentException("A consulta deve solicitar ao menos uma transação.");
    }

    return jdbcClient.sql(FIND_AFTER)
        .param("importId", importId)
        .param("afterId", afterId)
        .param("maxRows", maxRows)
        .query((resultSet, rowNumber) -> new IngestionTransactionRecord(
            resultSet.getLong("id"),
            resultSet.getLong("source_row"),
            resultSet.getString("transaction_id"),
            resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
            resultSet.getBigDecimal("amount"),
            resultSet.getString("category")))
        .list();
  }
}
