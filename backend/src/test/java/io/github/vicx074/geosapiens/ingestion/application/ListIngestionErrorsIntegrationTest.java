package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class ListIngestionErrorsIntegrationTest {

  private static final UUID JOB_ID = UUID.fromString("2820f65e-71c7-4fa1-9173-68dd37c99d1b");
  private static final UUID OTHER_JOB_ID = UUID.fromString("38e225d5-751b-4de1-a24f-93f86c1cb963");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-26T13:00:00Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Autowired
  private IngestionJobRepository jobs;

  @Autowired
  private ListIngestionErrors listErrors;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanDatabase() {
    jdbcClient.sql(
        "TRUNCATE TABLE ingestion_errors, transactions, ingestion_outbox, ingestion_jobs")
        .update();
  }

  @Test
  void shouldPaginateBySourceRowWithoutDuplicatesOrOffset() {
    insertJob(JOB_ID);
    insertJob(OTHER_JOB_ID);
    insertError(JOB_ID, 2, "AMOUNT_INVALID", "amount deve ser um decimal válido.");
    insertError(JOB_ID, 5, "CATEGORY_REQUIRED", "category é obrigatório.");
    insertError(JOB_ID, 8, "AMOUNT_ZERO", "amount não pode ser zero.");
    insertError(JOB_ID, 13, "OCCURRED_AT_INVALID", "occurred_at deve ser um instante ISO-8601 válido.");
    insertError(JOB_ID, 21, "TRANSACTION_ID_REQUIRED", "transaction_id é obrigatório.");
    insertError(OTHER_JOB_ID, 3, "AMOUNT_INVALID", "Erro de outra importação.");

    IngestionErrorPage first = listErrors.execute(JOB_ID, 2, null);
    IngestionErrorPage second = listErrors.execute(JOB_ID, 2, first.nextCursor());
    IngestionErrorPage third = listErrors.execute(JOB_ID, 2, second.nextCursor());

    assertThat(first.items()).extracting(IngestionErrorRecord::sourceRow).containsExactly(2L, 5L);
    assertThat(first.nextCursor()).isEqualTo(5L);
    assertThat(second.items()).extracting(IngestionErrorRecord::sourceRow).containsExactly(8L, 13L);
    assertThat(second.nextCursor()).isEqualTo(13L);
    assertThat(third.items()).extracting(IngestionErrorRecord::sourceRow).containsExactly(21L);
    assertThat(third.nextCursor()).isNull();
  }

  @Test
  void shouldReuseUniqueIndexThatMatchesTheErrorCursorQuery() {
    String definition = jdbcClient.sql("""
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename = 'ingestion_errors'
              AND indexname = 'uq_ingestion_errors_import_source_row'
            """)
        .query(String.class)
        .single();

    assertThat(definition)
        .contains("UNIQUE INDEX")
        .contains("(import_id, source_row)");
  }

  @Test
  void shouldRejectInvalidPageAndMissingImport() {
    assertThatThrownBy(() -> listErrors.execute(JOB_ID, 0, null))
        .isInstanceOf(InvalidIngestionPaginationException.class)
        .hasMessageContaining("entre 1 e 200");
    assertThatThrownBy(() -> listErrors.execute(JOB_ID, 50, -1L))
        .isInstanceOf(InvalidIngestionPaginationException.class)
        .hasMessageContaining("não pode ser negativo");
    assertThatThrownBy(() -> listErrors.execute(JOB_ID, 50, null))
        .isInstanceOf(IngestionJobNotFoundException.class);
  }

  private void insertJob(UUID jobId) {
    jobs.insert(IngestionJob.receive(jobId, "transactions.csv", RECEIVED_AT));
  }

  private void insertError(UUID jobId, long sourceRow, String code, String reason) {
    jdbcClient.sql("""
            INSERT INTO ingestion_errors (import_id, source_row, error_code, reason)
            VALUES (:jobId, :sourceRow, :code, :reason)
            """)
        .param("jobId", jobId)
        .param("sourceRow", sourceRow)
        .param("code", code)
        .param("reason", reason)
        .update();
  }
}
