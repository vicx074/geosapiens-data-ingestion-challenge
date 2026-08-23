package com.github.vicx074.geosapiens.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionJobTest {

  private static final UUID JOB_ID = UUID.fromString("cbb9f567-3ae5-4f41-87b0-221a2f436eca");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-23T12:00:00Z");
  private static final Instant QUEUED_AT = Instant.parse("2026-08-23T12:00:01Z");
  private static final Instant STARTED_AT = Instant.parse("2026-08-23T12:00:02Z");
  private static final Instant BATCH_AT = Instant.parse("2026-08-23T12:00:03Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-08-23T12:00:04Z");

  @Test
  void shouldCreateReceivedJobWithEmptyProgress() {
    IngestionJob job = newJob();

    assertThat(job.getId()).isEqualTo(JOB_ID);
    assertThat(job.getOriginalFilename()).isEqualTo("transactions.csv");
    assertThat(job.getStatus()).isEqualTo(IngestionStatus.RECEIVED);
    assertThat(job.getProcessedRows()).isZero();
    assertThat(job.getAcceptedRows()).isZero();
    assertThat(job.getRejectedRows()).isZero();
    assertThat(job.getCreatedAt()).isEqualTo(RECEIVED_AT);
    assertThat(job.getUpdatedAt()).isEqualTo(RECEIVED_AT);
    assertThat(job.getQueuedAt()).isEmpty();
    assertThat(job.getStartedAt()).isEmpty();
    assertThat(job.getFinishedAt()).isEmpty();
    assertThat(job.getFailureReason()).isEmpty();
  }

  @Test
  void shouldCompleteNominalFlowWithoutErrors() {
    IngestionJob job = processingJob();

    job.recordBatch(2_000, 0, BATCH_AT);
    job.complete(FINISHED_AT);

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
    assertThat(job.getProcessedRows()).isEqualTo(2_000);
    assertThat(job.getAcceptedRows()).isEqualTo(2_000);
    assertThat(job.getRejectedRows()).isZero();
    assertThat(job.getFinishedAt()).contains(FINISHED_AT);
    assertThat(job.getQueuedAt()).contains(QUEUED_AT);
    assertThat(job.getStartedAt()).contains(STARTED_AT);
  }

  @Test
  void shouldCompleteWithErrorsWhenAnyRowWasRejected() {
    IngestionJob job = processingJob();

    job.recordBatch(1_997, 3, BATCH_AT);
    job.complete(FINISHED_AT);

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.COMPLETED_WITH_ERRORS);
    assertThat(job.getProcessedRows()).isEqualTo(2_000);
    assertThat(job.getAcceptedRows()).isEqualTo(1_997);
    assertThat(job.getRejectedRows()).isEqualTo(3);
  }

  @Test
  void shouldAccumulateProgressOnlyByBatch() {
    IngestionJob job = processingJob();

    job.recordBatch(900, 100, BATCH_AT);
    job.recordBatch(995, 5, FINISHED_AT);

    assertThat(job.getProcessedRows()).isEqualTo(2_000);
    assertThat(job.getAcceptedRows()).isEqualTo(1_895);
    assertThat(job.getRejectedRows()).isEqualTo(105);
    assertThat(job.getUpdatedAt()).isEqualTo(FINISHED_AT);
  }

  @Test
  void shouldAllowProcessingStartToBeRepeatedAfterRedelivery() {
    IngestionJob job = processingJob();

    job.startProcessing(FINISHED_AT);

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.PROCESSING);
    assertThat(job.getStartedAt()).contains(STARTED_AT);
    assertThat(job.getUpdatedAt()).isEqualTo(STARTED_AT);
  }

  @Test
  void shouldRejectTransitionThatSkipsQueue() {
    IngestionJob job = newJob();

    assertThatExceptionOfType(InvalidIngestionJobTransitionException.class)
        .isThrownBy(() -> job.startProcessing(STARTED_AT))
        .satisfies(exception -> {
          assertThat(exception.getCurrentStatus()).isEqualTo(IngestionStatus.RECEIVED);
          assertThat(exception.getTargetStatus()).isEqualTo(IngestionStatus.PROCESSING);
        });
  }

  @Test
  void shouldRejectCompletionBeforeProcessing() {
    IngestionJob job = newJob();

    assertThatExceptionOfType(InvalidIngestionJobTransitionException.class)
        .isThrownBy(() -> job.complete(FINISHED_AT));
  }

  @Test
  void shouldRejectProgressOutsideProcessing() {
    IngestionJob job = newJob();

    assertThatExceptionOfType(InvalidIngestionJobTransitionException.class)
        .isThrownBy(() -> job.recordBatch(1, 0, BATCH_AT));
  }

  @Test
  void shouldRejectEmptyOrNegativeBatchCounters() {
    IngestionJob job = processingJob();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> job.recordBatch(0, 0, BATCH_AT))
        .withMessage("O lote deve conter ao menos uma linha.");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> job.recordBatch(-1, 0, BATCH_AT))
        .withMessage("Os contadores do lote não podem ser negativos.");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> job.recordBatch(0, -1, BATCH_AT))
        .withMessage("Os contadores do lote não podem ser negativos.");
  }

  @Test
  void shouldNotPartiallyChangeCountersOnOverflow() {
    IngestionJob job = processingJob();
    job.recordBatch(Long.MAX_VALUE, 0, BATCH_AT);

    assertThatExceptionOfType(ArithmeticException.class)
        .isThrownBy(() -> job.recordBatch(1, 1, FINISHED_AT));
    assertThat(job.getAcceptedRows()).isEqualTo(Long.MAX_VALUE);
    assertThat(job.getRejectedRows()).isZero();
    assertThat(job.getProcessedRows()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void shouldFailNonTerminalJobWithExplicitReason() {
    IngestionJob job = processingJob();

    job.fail("Arquivo temporário corrompido", FINISHED_AT);

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.FAILED);
    assertThat(job.getFailureReason()).contains("Arquivo temporário corrompido");
    assertThat(job.getFinishedAt()).contains(FINISHED_AT);
  }

  @Test
  void shouldAllowFailureBeforeProcessingStarts() {
    IngestionJob job = newJob();

    job.fail("Não foi possível publicar o job", QUEUED_AT);

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.FAILED);
    assertThat(job.getStartedAt()).isEmpty();
    assertThat(job.getFailureReason()).contains("Não foi possível publicar o job");
  }

  @Test
  void shouldRejectFailureWithoutReason() {
    IngestionJob job = processingJob();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> job.fail(" ", FINISHED_AT))
        .withMessage("O motivo da falha é obrigatório.");
  }

  @Test
  void shouldKeepCompletedJobTerminal() {
    IngestionJob job = processingJob();
    job.complete(FINISHED_AT);

    assertThatExceptionOfType(InvalidIngestionJobTransitionException.class)
        .isThrownBy(() -> job.startProcessing(FINISHED_AT));
    assertThatExceptionOfType(InvalidIngestionJobTransitionException.class)
        .isThrownBy(() -> job.fail("Falha tardia", FINISHED_AT));
    assertThat(job.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
  }

  @Test
  void shouldKeepFailedJobTerminal() {
    IngestionJob job = processingJob();
    job.fail("Falha definitiva", FINISHED_AT);

    assertThatExceptionOfType(InvalidIngestionJobTransitionException.class)
        .isThrownBy(() -> job.markQueued(FINISHED_AT));
    assertThatExceptionOfType(InvalidIngestionJobTransitionException.class)
        .isThrownBy(() -> job.startProcessing(FINISHED_AT));
    assertThat(job.getStatus()).isEqualTo(IngestionStatus.FAILED);
  }

  @Test
  void shouldIdentifyOnlyFinishedStatusesAsTerminal() {
    assertThat(IngestionStatus.RECEIVED.isTerminal()).isFalse();
    assertThat(IngestionStatus.QUEUED.isTerminal()).isFalse();
    assertThat(IngestionStatus.PROCESSING.isTerminal()).isFalse();
    assertThat(IngestionStatus.COMPLETED.isTerminal()).isTrue();
    assertThat(IngestionStatus.COMPLETED_WITH_ERRORS.isTerminal()).isTrue();
    assertThat(IngestionStatus.FAILED.isTerminal()).isTrue();
  }

  private static IngestionJob newJob() {
    return IngestionJob.receive(JOB_ID, "transactions.csv", RECEIVED_AT);
  }

  private static IngestionJob processingJob() {
    IngestionJob job = newJob();
    job.markQueued(QUEUED_AT);
    job.startProcessing(STARTED_AT);
    return job;
  }
}
