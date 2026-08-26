package com.github.vicx074.geosapiens.ingestion.infrastructure.persistence;

import com.github.vicx074.geosapiens.ingestion.application.port.out.JobPublicationOutbox;
import com.github.vicx074.geosapiens.ingestion.application.port.out.PendingJobPublication;
import java.sql.Types;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public final class PostgresJobPublicationOutbox implements JobPublicationOutbox {

  private final JdbcClient jdbcClient;

  public PostgresJobPublicationOutbox(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void insert(PendingJobPublication publication) {
    int affectedRows = jdbcClient.sql("""
            INSERT INTO ingestion_outbox (
                job_id,
                status,
                attempts,
                created_at,
                available_at
            ) VALUES (
                :jobId,
                'PENDING',
                0,
                :createdAt,
                :availableAt
            )
            """)
        .param("jobId", publication.jobId())
        .param(
            "createdAt",
            publication.createdAt().atOffset(ZoneOffset.UTC),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "availableAt",
            publication.createdAt().atOffset(ZoneOffset.UTC),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .update();

    if (affectedRows != 1) {
      throw new IllegalStateException(
          "A criação da publicação pendente deveria alterar exatamente uma linha.");
    }
  }
}
