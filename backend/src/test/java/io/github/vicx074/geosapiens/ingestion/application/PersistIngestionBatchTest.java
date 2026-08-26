package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vicx074.geosapiens.ingestion.application.port.out.BatchInsertResult;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvRowError;
import io.github.vicx074.geosapiens.ingestion.application.port.out.CsvTransactionRow;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionRecordRepository;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
class PersistIngestionBatchTest {

  private static final UUID JOB_ID = UUID.fromString("77ab89c9-c696-42cf-8280-ab033df61073");
  private static final Instant BASE_TIME = Instant.parse("2026-08-26T12:00:00Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Autowired
  private IngestionJobRepository jobs;

  @Autowired
  private IngestionRecordRepository records;

  @Autowired
  private PersistIngestionBatch persistBatch;

  @Autowired
  private TransactionRunner transactionRunner;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanDatabase() {
    jdbcClient.sql(
        "TRUNCATE TABLE ingestion_errors, transactions, ingestion_outbox, ingestion_jobs")
        .update();
  }

  @Test
  void shouldPersistBatchAndKeepRedeliveryIdempotent() {
    insertProcessingJob();
    CsvTransactionRow accepted = new CsvTransactionRow(
        2,
        "txn-1",
        Instant.parse("2025-01-02T03:04:05Z"),
        new BigDecimal("10.50"),
        "transporte");
    CsvRowError rejected = new CsvRowError(
        3,
        "TRANSACTION_ID_REQUIRED",
        "transaction_id é obrigatório.");

    BatchInsertResult first = persistBatch.execute(
        JOB_ID,
        List.of(accepted),
        List.of(rejected));
    BatchInsertResult redelivery = persistBatch.execute(
        JOB_ID,
        List.of(accepted),
        List.of(rejected));

    assertThat(first.acceptedRows()).isEqualTo(1);
    assertThat(first.rejectedRows()).isEqualTo(1);
    assertThat(redelivery.processedRows()).isZero();
    assertThat(count("transactions")).isEqualTo(1);
    assertThat(count("ingestion_errors")).isEqualTo(1);
    assertThat(jobs.findById(JOB_ID)).get().satisfies(versioned -> {
      assertThat(versioned.job().getAcceptedRows()).isEqualTo(1);
      assertThat(versioned.job().getRejectedRows()).isEqualTo(1);
    });
  }

  @Test
  void shouldRollbackInsertedRowsWhenJobProgressCannotBeUpdated() {
    insertProcessingJob();
    IngestionJobRepository failingJobs = mock(IngestionJobRepository.class);
    when(failingJobs.findById(JOB_ID)).thenReturn(jobs.findById(JOB_ID));
    when(failingJobs.update(any())).thenThrow(new IllegalStateException("Falha simulada no job"));
    PersistIngestionBatch failingUseCase = new PersistIngestionBatch(
        records,
        failingJobs,
        transactionRunner,
        Clock.fixed(BASE_TIME.plusSeconds(10), ZoneOffset.UTC));
    CsvTransactionRow accepted = new CsvTransactionRow(
        2,
        "txn-rollback",
        Instant.parse("2025-01-02T03:04:05Z"),
        new BigDecimal("20.00"),
        "serviços");

    assertThatThrownBy(() -> failingUseCase.execute(JOB_ID, List.of(accepted), List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Falha simulada");

    assertThat(count("transactions")).isZero();
    assertThat(jobs.findById(JOB_ID)).get().satisfies(versioned ->
        assertThat(versioned.job().getProcessedRows()).isZero());
  }

  private void insertProcessingJob() {
    IngestionJob job = IngestionJob.receive(JOB_ID, "transactions.csv", BASE_TIME);
    job.markQueued(BASE_TIME.plusSeconds(1));
    job.startProcessing(BASE_TIME.plusSeconds(2));
    jobs.insert(job);
  }

  private long count(String table) {
    return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
        .query(Long.class)
        .single();
  }
}
