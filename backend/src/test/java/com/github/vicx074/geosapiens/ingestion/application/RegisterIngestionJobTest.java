package com.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobPublicationOutbox;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
import com.github.vicx074.geosapiens.shared.application.TransactionRunner;
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
class RegisterIngestionJobTest {

  private static final UUID JOB_ID = UUID.fromString("cbb9f567-3ae5-4f41-87b0-221a2f436eca");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-23T12:00:00Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:17.11-alpine3.24");

  @Autowired
  private IngestionJobRepository repository;

  @Autowired
  private RegisterIngestionJob registerIngestionJob;

  @Autowired
  private TransactionRunner transactionRunner;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanDatabase() {
    jdbcClient.sql("TRUNCATE TABLE ingestion_outbox").update();
    jdbcClient.sql("TRUNCATE TABLE ingestion_jobs").update();
  }

  @Test
  void shouldRegisterJobAndPublicationInSameTransaction() {
    IngestionJob job = registerIngestionJob.execute(JOB_ID, "transactions.csv", RECEIVED_AT);

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.RECEIVED);
    assertThat(repository.findById(JOB_ID)).isPresent();
    assertThat(jdbcClient.sql("""
            SELECT COUNT(*)
            FROM ingestion_outbox
            WHERE job_id = :jobId
              AND status = 'PENDING'
            """)
        .param("jobId", JOB_ID)
        .query(Long.class)
        .single()).isEqualTo(1);
  }

  @Test
  void shouldRollbackJobWhenOutboxInsertionFails() {
    JobPublicationOutbox failingOutbox = publication -> {
      throw new IllegalStateException("Falha simulada no Outbox");
    };
    RegisterIngestionJob useCase =
        new RegisterIngestionJob(repository, failingOutbox, transactionRunner);

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> useCase.execute(JOB_ID, "transactions.csv", RECEIVED_AT));
    assertThat(repository.findById(JOB_ID)).isEmpty();
  }
}
