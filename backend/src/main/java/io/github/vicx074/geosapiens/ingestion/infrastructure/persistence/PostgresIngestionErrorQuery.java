package io.github.vicx074.geosapiens.ingestion.infrastructure.persistence;

import io.github.vicx074.geosapiens.ingestion.application.IngestionErrorRecord;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionErrorQuery;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresIngestionErrorQuery implements IngestionErrorQuery {

  private static final String FIND_AFTER = """
      SELECT source_row, error_code, reason
      FROM ingestion_errors
      WHERE import_id = :importId
        AND source_row > :afterSourceRow
      ORDER BY source_row
      LIMIT :maxRows
      """;

  private final JdbcClient jdbcClient;

  public PostgresIngestionErrorQuery(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public List<IngestionErrorRecord> findAfter(UUID importId, long afterSourceRow, int maxRows) {
    Objects.requireNonNull(importId, "O identificador da importação é obrigatório.");
    if (afterSourceRow < 0) {
      throw new IllegalArgumentException("A linha usada como cursor não pode ser negativa.");
    }
    if (maxRows < 1) {
      throw new IllegalArgumentException("A consulta deve solicitar ao menos uma linha.");
    }

    return jdbcClient.sql(FIND_AFTER)
        .param("importId", importId)
        .param("afterSourceRow", afterSourceRow)
        .param("maxRows", maxRows)
        .query((resultSet, rowNumber) -> new IngestionErrorRecord(
            resultSet.getLong("source_row"),
            resultSet.getString("error_code"),
            resultSet.getString("reason")))
        .list();
  }
}
