package io.github.vicx074.geosapiens.ingestion.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.vicx074.geosapiens.ingestion.application.port.out.ConcurrentIngestionJobUpdateException;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
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
class PostgresIngestionJobRepositoryTest {

  private static final UUID JOB_ID = UUID.fromString("cbb9f567-3ae5-4f41-87b0-221a2f436eca");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-23T12:00:00Z");
  private static final Instant QUEUED_AT = Instant.parse("2026-08-23T12:00:01Z");
  private static final Instant STARTED_AT = Instant.parse("2026-08-23T12:00:02Z");
  private static final Instant BATCH_AT = Instant.parse("2026-08-23T12:00:03Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Autowired
  private IngestionJobRepository repository;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanDatabase() {
    jdbcClient.sql("TRUNCATE TABLE ingestion_outbox, ingestion_jobs").update();
  }

  @Test
  void shouldApplyMigrationAndRestorePersistedJob() {
    IngestionJob job = IngestionJob.receive(JOB_ID, "transações.csv", RECEIVED_AT);
    job.markQueued(QUEUED_AT);
    job.startProcessing(STARTED_AT);
    job.recordBatch(998, 2, BATCH_AT);

    repository.insert(job);

    VersionedIngestionJob persisted = repository.findById(JOB_ID).orElseThrow();
    assertThat(persisted.version()).isZero();
    assertThat(persisted.job().getOriginalFilename()).isEqualTo("transações.csv");
    assertThat(persisted.job().getStatus()).isEqualTo(IngestionStatus.PROCESSING);
    assertThat(persisted.job().getAcceptedRows()).isEqualTo(998);
    assertThat(persisted.job().getRejectedRows()).isEqualTo(2);
    assertThat(persisted.job().getCreatedAt()).isEqualTo(RECEIVED_AT);
    assertThat(persisted.job().getQueuedAt()).contains(QUEUED_AT);
    assertThat(persisted.job().getStartedAt()).contains(STARTED_AT);
    assertThat(persisted.job().getUpdatedAt()).isEqualTo(BATCH_AT);
  }

  @Test
  void shouldRejectStaleConcurrentUpdate() {
    repository.insert(IngestionJob.receive(JOB_ID, "transactions.csv", RECEIVED_AT));
    VersionedIngestionJob firstReader = repository.findById(JOB_ID).orElseThrow();
    VersionedIngestionJob secondReader = repository.findById(JOB_ID).orElseThrow();

    firstReader.job().markQueued(QUEUED_AT);
    VersionedIngestionJob updated = repository.update(firstReader);

    secondReader.job().fail("Falha concorrente", QUEUED_AT);
    assertThatExceptionOfType(ConcurrentIngestionJobUpdateException.class)
        .isThrownBy(() -> repository.update(secondReader));
    assertThat(updated.version()).isEqualTo(1);
    assertThat(repository.findById(JOB_ID).orElseThrow().job().getStatus())
        .isEqualTo(IngestionStatus.QUEUED);
  }

  @Test
  void shouldReturnEmptyWhenJobDoesNotExist() {
    assertThat(repository.findById(JOB_ID)).isEmpty();
  }
}
