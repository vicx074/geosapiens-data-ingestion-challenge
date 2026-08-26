package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.StoredTemporaryFile;
import io.github.vicx074.geosapiens.ingestion.application.port.out.TemporaryFileStorage;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
class ProcessIngestionJobTest {

  private static final UUID JOB_ID = UUID.fromString("0a57bc5a-481e-4de4-a58b-cc409a57f198");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-26T12:00:00Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Autowired
  private IngestionJobRepository jobs;

  @Autowired
  private TemporaryFileStorage storage;

  @Autowired
  private ProcessIngestionJob processJob;

  @Autowired
  private JdbcClient jdbcClient;

  private String storedKey;

  @BeforeEach
  void cleanDatabaseAndStorage() throws Exception {
    jdbcClient.sql("TRUNCATE TABLE ingestion_outbox, ingestion_jobs").update();
    storage.delete(JOB_ID + ".csv");
    storedKey = null;
  }

  @AfterEach
  void cleanStorage() throws Exception {
    if (storedKey != null) {
      storage.delete(storedKey);
    }
  }

  @Test
  void shouldProcessCsvAndPersistTerminalCounters() throws Exception {
    queueJob();
    storedKey = store("""
        transaction_id,occurred_at,amount,category
        txn-1,2025-01-02T03:04:05Z,10.50,transporte
        txn-2,2025-01-03T03:04:05Z,15.00,saúde
        ,2025-01-04T03:04:05Z,20.00,lazer
        """);

    IngestionJob processed = processJob.execute(JOB_ID);

    assertThat(processed.getStatus()).isEqualTo(IngestionStatus.COMPLETED_WITH_ERRORS);
    assertThat(processed.getAcceptedRows()).isEqualTo(2);
    assertThat(processed.getRejectedRows()).isEqualTo(1);
    assertThat(jobs.findById(JOB_ID)).get().satisfies(versioned -> {
      assertThat(versioned.job().getStatus()).isEqualTo(IngestionStatus.COMPLETED_WITH_ERRORS);
      assertThat(versioned.job().getProcessedRows()).isEqualTo(3);
    });
  }

  @Test
  void shouldFailJobWhenHeaderDoesNotMatchContract() throws Exception {
    queueJob();
    storedKey = store("id,occurred_at,amount,category\n");

    IngestionJob processed = processJob.execute(JOB_ID);

    assertThat(processed.getStatus()).isEqualTo(IngestionStatus.FAILED);
    assertThat(processed.getFailureReason()).hasValueSatisfying(
        reason -> assertThat(reason).contains("Cabeçalho CSV inválido"));
  }

  @Test
  void shouldTreatTerminalRedeliveryAsIdempotentNoOp() throws Exception {
    queueJob();
    storedKey = store("""
        transaction_id,occurred_at,amount,category
        txn-1,2025-01-02T03:04:05Z,10.50,transporte
        """);
    processJob.execute(JOB_ID);

    IngestionJob redelivered = processJob.execute(JOB_ID);

    assertThat(redelivered.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
    assertThat(redelivered.getAcceptedRows()).isEqualTo(1);
  }

  private void queueJob() {
    IngestionJob job = IngestionJob.receive(JOB_ID, "transactions.csv", RECEIVED_AT);
    job.markQueued(RECEIVED_AT.plusSeconds(1));
    jobs.insert(job);
  }

  private String store(String csv) throws Exception {
    StoredTemporaryFile stored = storage.store(
        JOB_ID,
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    return stored.storageKey();
  }
}
