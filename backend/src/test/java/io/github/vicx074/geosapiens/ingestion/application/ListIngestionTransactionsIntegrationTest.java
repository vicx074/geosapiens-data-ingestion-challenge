package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class ListIngestionTransactionsIntegrationTest {

  private static final UUID JOB_ID = UUID.fromString("b908c7cc-5e85-4a5c-b31c-bd6bcf63839e");
  private static final UUID OTHER_JOB_ID = UUID.fromString("fa45c504-b92f-4ea2-bc77-aa36a283cb50");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-26T14:00:00Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Autowired
  private IngestionJobRepository jobs;

  @Autowired
  private ListIngestionTransactions listTransactions;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanDatabase() {
    jdbcClient.sql(
        "TRUNCATE TABLE ingestion_errors, transactions, ingestion_outbox, ingestion_jobs RESTART IDENTITY")
        .update();
  }

  @Test
  void shouldPaginateByPersistedIdWithoutDuplicatesAndIgnoreOtherImports() {
    insertJob(JOB_ID);
    insertJob(OTHER_JOB_ID);
    long firstId = insertTransaction(JOB_ID, 2, "txn-1", "10.50", "transporte");
    insertTransaction(OTHER_JOB_ID, 3, "other-1", "99.90", "outro");
    long secondId = insertTransaction(JOB_ID, 5, "txn-2", "20.00", "saúde");
    long thirdId = insertTransaction(JOB_ID, 8, "txn-3", "30.25", "lazer");
    long fourthId = insertTransaction(JOB_ID, 13, "txn-4", "40.75", "alimentação");
    long fifthId = insertTransaction(JOB_ID, 21, "txn-5", "50.10", "educação");

    IngestionTransactionPage first = listTransactions.execute(JOB_ID, 2, null);
    IngestionTransactionPage second = listTransactions.execute(JOB_ID, 2, first.nextCursor());
    IngestionTransactionPage third = listTransactions.execute(JOB_ID, 2, second.nextCursor());

    assertThat(first.items()).extracting(IngestionTransactionRecord::id)
        .containsExactly(firstId, secondId);
    assertThat(first.nextCursor()).isEqualTo(secondId);
    assertThat(second.items()).extracting(IngestionTransactionRecord::id)
        .containsExactly(thirdId, fourthId);
    assertThat(second.nextCursor()).isEqualTo(fourthId);
    assertThat(third.items()).extracting(IngestionTransactionRecord::id)
        .containsExactly(fifthId);
    assertThat(third.nextCursor()).isNull();
  }

  @Test
  void shouldCreateIndexThatMatchesTheTransactionCursorQuery() {
    String definition = jdbcClient.sql("""
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename = 'transactions'
              AND indexname = 'idx_transactions_import_cursor'
            """)
        .query(String.class)
        .single();

    assertThat(definition)
        .contains("INDEX")
        .contains("(import_id, id)");
  }

  @Test
  void shouldReturnPersistedFinancialFieldsWithoutChangingPrecision() {
    insertJob(JOB_ID);
    long id = insertTransaction(JOB_ID, 2, "txn-precision", "123456.78", "serviços");

    IngestionTransactionRecord transaction = listTransactions.execute(JOB_ID, 10, null)
        .items()
        .getFirst();

    assertThat(transaction.id()).isEqualTo(id);
    assertThat(transaction.sourceRow()).isEqualTo(2);
    assertThat(transaction.transactionId()).isEqualTo("txn-precision");
    assertThat(transaction.occurredAt()).isEqualTo(Instant.parse("2025-01-02T03:04:05Z"));
    assertThat(transaction.amount()).isEqualByComparingTo("123456.78");
    assertThat(transaction.category()).isEqualTo("serviços");
  }

  @Test
  void shouldRejectInvalidPageAndMissingImport() {
    assertThatThrownBy(() -> listTransactions.execute(JOB_ID, 0, null))
        .isInstanceOf(InvalidIngestionPaginationException.class)
        .hasMessageContaining("entre 1 e 200");
    assertThatThrownBy(() -> listTransactions.execute(JOB_ID, 50, -1L))
        .isInstanceOf(InvalidIngestionPaginationException.class)
        .hasMessageContaining("não pode ser negativo");
    assertThatThrownBy(() -> listTransactions.execute(JOB_ID, 50, null))
        .isInstanceOf(IngestionJobNotFoundException.class);
  }

  private void insertJob(UUID jobId) {
    jobs.insert(IngestionJob.receive(jobId, "transactions.csv", RECEIVED_AT));
  }

  private long insertTransaction(
      UUID jobId,
      long sourceRow,
      String transactionId,
      String amount,
      String category) {
    return jdbcClient.sql("""
            INSERT INTO transactions (
                import_id,
                source_row,
                transaction_id,
                occurred_at,
                amount,
                category
            ) VALUES (
                :jobId,
                :sourceRow,
                :transactionId,
                :occurredAt,
                :amount,
                :category
            )
            RETURNING id
            """)
        .param("jobId", jobId)
        .param("sourceRow", sourceRow)
        .param("transactionId", transactionId)
        .param("occurredAt", OffsetDateTime.ofInstant(
            Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC))
        .param("amount", new BigDecimal(amount))
        .param("category", category)
        .query(Long.class)
        .single();
  }
}
