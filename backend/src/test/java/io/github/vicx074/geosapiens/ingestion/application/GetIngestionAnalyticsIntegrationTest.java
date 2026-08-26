package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics.CategoryAggregate;
import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics.MonthAggregate;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
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
class GetIngestionAnalyticsIntegrationTest {

  private static final UUID JOB_ID = UUID.fromString("ab930ec3-0821-4fc7-b6ca-acd4161a45af");
  private static final UUID OTHER_JOB_ID = UUID.fromString("2b386ff4-fbea-4188-bec1-6191ac6d0cd6");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-26T14:00:00Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Autowired
  private IngestionJobRepository jobs;

  @Autowired
  private GetIngestionAnalytics getAnalytics;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanDatabase() {
    jdbcClient.sql(
        "TRUNCATE TABLE ingestion_errors, transactions, ingestion_outbox, ingestion_jobs")
        .update();
  }

  @Test
  void shouldAggregateTotalCategoryAndUtcMonthWithoutMixingImports() {
    insertJob(JOB_ID);
    insertJob(OTHER_JOB_ID);
    insertTransaction(JOB_ID, 2, "txn-1", "2025-01-05T10:00:00Z", "100.00", "alimentação");
    insertTransaction(JOB_ID, 3, "txn-2", "2025-01-31T23:59:59Z", "-25.50", "alimentação");
    insertTransaction(JOB_ID, 4, "txn-3", "2025-02-01T00:00:00Z", "50.25", "transporte");
    insertTransaction(OTHER_JOB_ID, 2, "other-1", "2025-01-01T00:00:00Z", "999.99", "outro");

    IngestionAnalytics result = getAnalytics.execute(JOB_ID);

    assertThat(result.transactionCount()).isEqualTo(3);
    assertThat(result.totalAmount()).isEqualByComparingTo("124.75");
    assertThat(result.byCategory())
        .extracting(CategoryAggregate::category)
        .containsExactly("alimentação", "transporte");
    assertThat(result.byCategory().get(0).transactionCount()).isEqualTo(2);
    assertThat(result.byCategory().get(0).totalAmount()).isEqualByComparingTo("74.50");
    assertThat(result.byCategory().get(1).totalAmount()).isEqualByComparingTo("50.25");
    assertThat(result.byMonth())
        .extracting(MonthAggregate::month)
        .containsExactly(YearMonth.of(2025, 1), YearMonth.of(2025, 2));
    assertThat(result.byMonth().get(0).totalAmount()).isEqualByComparingTo("74.50");
    assertThat(result.byMonth().get(1).totalAmount()).isEqualByComparingTo("50.25");
  }

  @Test
  void shouldReturnZeroedAnalyticsForExistingImportWithoutTransactions() {
    insertJob(JOB_ID);

    IngestionAnalytics result = getAnalytics.execute(JOB_ID);

    assertThat(result.transactionCount()).isZero();
    assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.byCategory()).isEmpty();
    assertThat(result.byMonth()).isEmpty();
  }

  @Test
  void shouldRejectMissingImportAndCreateCoveringAnalyticsIndex() {
    assertThatThrownBy(() -> getAnalytics.execute(JOB_ID))
        .isInstanceOf(IngestionJobNotFoundException.class);

    String definition = jdbcClient.sql("""
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename = 'transactions'
              AND indexname = 'idx_transactions_analytics_by_import'
            """)
        .query(String.class)
        .single();

    assertThat(definition)
        .contains("(import_id)")
        .contains("INCLUDE")
        .contains("category")
        .contains("occurred_at")
        .contains("amount");
  }

  private void insertJob(UUID jobId) {
    jobs.insert(IngestionJob.receive(jobId, "transactions.csv", RECEIVED_AT));
  }

  private void insertTransaction(
      UUID jobId,
      long sourceRow,
      String transactionId,
      String occurredAt,
      String amount,
      String category) {
    jdbcClient.sql("""
            INSERT INTO transactions (
                import_id, source_row, transaction_id, occurred_at, amount, category
            ) VALUES (
                :jobId, :sourceRow, :transactionId, :occurredAt, :amount, :category
            )
            """)
        .param("jobId", jobId)
        .param("sourceRow", sourceRow)
        .param("transactionId", transactionId)
        .param("occurredAt", OffsetDateTime.parse(occurredAt))
        .param("amount", new BigDecimal(amount))
        .param("category", category)
        .update();
  }
}
