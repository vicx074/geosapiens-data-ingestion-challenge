package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.JobPublicationOutbox;
import io.github.vicx074.geosapiens.ingestion.application.port.out.LostOutboxClaimException;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
import io.github.vicx074.geosapiens.shared.application.TransactionRunner;
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
  private JobPublicationOutbox outbox;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanDatabase() {
    jdbcClient.sql("TRUNCATE TABLE ingestion_outbox, ingestion_jobs").update();
  }

  @Test
  void shouldRegisterJobAndPublicationInSameTransaction() {
    IngestionJob job = registerIngestionJob.execute(JOB_ID, "transactions.csv", RECEIVED_AT);

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.QUEUED);
    assertThat(job.getQueuedAt()).contains(RECEIVED_AT);
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
    JobPublicationOutbox failingOutbox = mock(JobPublicationOutbox.class);
    doThrow(new IllegalStateException("Falha simulada no Outbox"))
        .when(failingOutbox).insert(any());
    RegisterIngestionJob useCase =
        new RegisterIngestionJob(repository, failingOutbox, transactionRunner);

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> useCase.execute(JOB_ID, "transactions.csv", RECEIVED_AT));
    assertThat(repository.findById(JOB_ID)).isEmpty();
  }

  @Test
  void shouldClaimAndRescheduleAvailablePublication() {
    registerIngestionJob.execute(JOB_ID, "transactions.csv", RECEIVED_AT);
    UUID firstToken = UUID.fromString("ad455f67-ea8d-4422-b272-649a04de3215");
    UUID secondToken = UUID.fromString("1198c594-04ba-45e3-a98b-bfaef51ca26");

    var firstClaim = outbox.claimAvailable(
        RECEIVED_AT, RECEIVED_AT.plusSeconds(30), 1, firstToken);
    outbox.reschedule(
        JOB_ID, firstToken, RECEIVED_AT.plusSeconds(10), "RabbitMQ indisponível");
    var secondClaim = outbox.claimAvailable(
        RECEIVED_AT.plusSeconds(10), RECEIVED_AT.plusSeconds(40), 1, secondToken);

    assertThat(firstClaim).singleElement().satisfies(publication -> {
      assertThat(publication.attempts()).isEqualTo(1);
      assertThat(publication.claimToken()).isEqualTo(firstToken);
    });
    assertThat(secondClaim).singleElement().satisfies(publication -> {
      assertThat(publication.attempts()).isEqualTo(2);
      assertThat(publication.claimToken()).isEqualTo(secondToken);
    });
  }

  @Test
  void shouldRejectUpdateFromExpiredClaim() {
    registerIngestionJob.execute(JOB_ID, "transactions.csv", RECEIVED_AT);
    UUID activeToken = UUID.fromString("ad455f67-ea8d-4422-b272-649a04de3215");
    UUID staleToken = UUID.fromString("1198c594-04ba-45e3-a98b-bfaef51ca26");
    outbox.claimAvailable(RECEIVED_AT, RECEIVED_AT.plusSeconds(30), 1, activeToken);

    assertThatExceptionOfType(LostOutboxClaimException.class)
        .isThrownBy(() -> outbox.markPublished(JOB_ID, staleToken, RECEIVED_AT));
  }
}
