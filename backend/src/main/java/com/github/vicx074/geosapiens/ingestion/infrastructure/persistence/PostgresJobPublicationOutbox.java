package com.github.vicx074.geosapiens.ingestion.infrastructure.persistence;

import com.github.vicx074.geosapiens.ingestion.application.port.out.JobPublicationOutbox;
import com.github.vicx074.geosapiens.ingestion.application.port.out.ClaimedJobPublication;
import com.github.vicx074.geosapiens.ingestion.application.port.out.LostOutboxClaimException;
import com.github.vicx074.geosapiens.ingestion.application.port.out.PendingJobPublication;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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

  @Override
  public List<ClaimedJobPublication> claimAvailable(
      Instant now, Instant claimedUntil, int limit, UUID claimToken) {
    return jdbcClient.sql("""
            WITH candidates AS (
                SELECT job_id
                FROM ingestion_outbox
                WHERE (status = 'PENDING' AND available_at <= :now)
                   OR (status = 'CLAIMED' AND claimed_until <= :now)
                ORDER BY COALESCE(claimed_until, available_at), job_id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            UPDATE ingestion_outbox AS outbox
            SET status = 'CLAIMED',
                attempts = outbox.attempts + 1,
                claimed_at = :now,
                claimed_until = :claimedUntil,
                claim_token = :claimToken
            FROM candidates
            WHERE outbox.job_id = candidates.job_id
            RETURNING outbox.job_id, outbox.attempts, outbox.claim_token
            """)
        .param("now", now.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "claimedUntil",
            claimedUntil.atOffset(ZoneOffset.UTC),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .param("limit", limit)
        .param("claimToken", claimToken)
        .query((resultSet, rowNumber) -> new ClaimedJobPublication(
            resultSet.getObject("job_id", UUID.class),
            resultSet.getInt("attempts"),
            resultSet.getObject("claim_token", UUID.class)))
        .list();
  }

  @Override
  public void markPublished(UUID jobId, UUID claimToken, Instant publishedAt) {
    int affectedRows = jdbcClient.sql("""
            UPDATE ingestion_outbox
            SET status = 'PUBLISHED',
                claimed_at = NULL,
                claimed_until = NULL,
                claim_token = NULL,
                published_at = :publishedAt,
                last_error = NULL
            WHERE job_id = :jobId
              AND status = 'CLAIMED'
              AND claim_token = :claimToken
            """)
        .param("jobId", jobId)
        .param("claimToken", claimToken)
        .param(
            "publishedAt", publishedAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
    requireActiveClaim(jobId, affectedRows);
  }

  @Override
  public void reschedule(
      UUID jobId, UUID claimToken, Instant availableAt, String lastError) {
    int affectedRows = jdbcClient.sql("""
            UPDATE ingestion_outbox
            SET status = 'PENDING',
                available_at = :availableAt,
                claimed_at = NULL,
                claimed_until = NULL,
                claim_token = NULL,
                last_error = :lastError
            WHERE job_id = :jobId
              AND status = 'CLAIMED'
              AND claim_token = :claimToken
            """)
        .param("jobId", jobId)
        .param("claimToken", claimToken)
        .param(
            "availableAt", availableAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("lastError", lastError)
        .update();
    requireActiveClaim(jobId, affectedRows);
  }

  @Override
  public void markFailed(UUID jobId, UUID claimToken, Instant failedAt, String lastError) {
    int affectedRows = jdbcClient.sql("""
            UPDATE ingestion_outbox
            SET status = 'FAILED',
                claimed_at = NULL,
                claimed_until = NULL,
                claim_token = NULL,
                failed_at = :failedAt,
                last_error = :lastError
            WHERE job_id = :jobId
              AND status = 'CLAIMED'
              AND claim_token = :claimToken
            """)
        .param("jobId", jobId)
        .param("claimToken", claimToken)
        .param("failedAt", failedAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("lastError", lastError)
        .update();
    requireActiveClaim(jobId, affectedRows);
  }

  private static void requireActiveClaim(UUID jobId, int affectedRows) {
    if (affectedRows == 0) {
      throw new LostOutboxClaimException(jobId);
    }
    if (affectedRows != 1) {
      throw new IllegalStateException(
          "A atualização do Outbox deveria alterar exatamente uma linha.");
    }
  }
}
