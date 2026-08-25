package com.github.vicx074.geosapiens.ingestion.infrastructure.persistence;

import com.github.vicx074.geosapiens.ingestion.application.port.out.ConcurrentIngestionJobUpdateException;
import com.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import com.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public final class PostgresIngestionJobRepository implements IngestionJobRepository {

  private static final String JOB_COLUMNS = """
      id,
      original_filename,
      status,
      accepted_rows,
      rejected_rows,
      created_at,
      queued_at,
      started_at,
      finished_at,
      updated_at,
      failure_reason,
      version
      """;

  private final JdbcClient jdbcClient;

  public PostgresIngestionJobRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void insert(IngestionJob job) {
    int affectedRows = bindJob(jdbcClient.sql("""
            INSERT INTO ingestion_jobs (
                id,
                original_filename,
                status,
                accepted_rows,
                rejected_rows,
                created_at,
                queued_at,
                started_at,
                finished_at,
                updated_at,
                failure_reason,
                version
            ) VALUES (
                :id,
                :originalFilename,
                :status,
                :acceptedRows,
                :rejectedRows,
                :createdAt,
                :queuedAt,
                :startedAt,
                :finishedAt,
                :updatedAt,
                :failureReason,
                0
            )
            """), job)
        .update();

    if (affectedRows != 1) {
      throw new IllegalStateException("A criação do job deveria alterar exatamente uma linha.");
    }
  }

  @Override
  public Optional<VersionedIngestionJob> findById(UUID id) {
    return jdbcClient.sql("SELECT " + JOB_COLUMNS + " FROM ingestion_jobs WHERE id = :id")
        .param("id", id)
        .query(this::mapVersionedJob)
        .optional();
  }

  @Override
  public VersionedIngestionJob update(VersionedIngestionJob versionedJob) {
    long nextVersion = Math.addExact(versionedJob.version(), 1);

    int affectedRows = bindJob(jdbcClient.sql("""
            UPDATE ingestion_jobs
            SET original_filename = :originalFilename,
                status = :status,
                accepted_rows = :acceptedRows,
                rejected_rows = :rejectedRows,
                queued_at = :queuedAt,
                started_at = :startedAt,
                finished_at = :finishedAt,
                updated_at = :updatedAt,
                failure_reason = :failureReason,
                version = :nextVersion
            WHERE id = :id
              AND version = :expectedVersion
            """), versionedJob.job())
        .param("expectedVersion", versionedJob.version())
        .param("nextVersion", nextVersion)
        .update();

    if (affectedRows == 0) {
      throw new ConcurrentIngestionJobUpdateException(
          versionedJob.job().getId(), versionedJob.version());
    }
    if (affectedRows != 1) {
      throw new IllegalStateException("A atualização do job deveria alterar exatamente uma linha.");
    }
    return new VersionedIngestionJob(versionedJob.job(), nextVersion);
  }

  private JdbcClient.StatementSpec bindJob(JdbcClient.StatementSpec statement, IngestionJob job) {
    return statement
        .param("id", job.getId())
        .param("originalFilename", job.getOriginalFilename())
        .param("status", job.getStatus().name())
        .param("acceptedRows", job.getAcceptedRows())
        .param("rejectedRows", job.getRejectedRows())
        .param("createdAt", atUtc(job.getCreatedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("queuedAt", atUtc(job.getQueuedAt().orElse(null)), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("startedAt", atUtc(job.getStartedAt().orElse(null)), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("finishedAt", atUtc(job.getFinishedAt().orElse(null)), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("updatedAt", atUtc(job.getUpdatedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("failureReason", job.getFailureReason().orElse(null), Types.VARCHAR);
  }

  private VersionedIngestionJob mapVersionedJob(ResultSet resultSet, int rowNumber)
      throws SQLException {
    IngestionJob job = IngestionJob.restore(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("original_filename"),
        IngestionStatus.valueOf(resultSet.getString("status")),
        resultSet.getLong("accepted_rows"),
        resultSet.getLong("rejected_rows"),
        instant(resultSet, "created_at"),
        instant(resultSet, "queued_at"),
        instant(resultSet, "started_at"),
        instant(resultSet, "finished_at"),
        instant(resultSet, "updated_at"),
        resultSet.getString("failure_reason"));

    return new VersionedIngestionJob(job, resultSet.getLong("version"));
  }

  private static OffsetDateTime atUtc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet resultSet, String column) throws SQLException {
    OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
