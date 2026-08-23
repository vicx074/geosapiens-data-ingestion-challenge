package com.github.vicx074.geosapiens.ingestion.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agregado que preserva as transições e os contadores de uma importação sem depender de
 * HTTP, mensageria ou persistência.
 */
public final class IngestionJob {

  private final UUID id;
  private final String originalFilename;
  private final Instant createdAt;
  private IngestionStatus status;
  private long acceptedRows;
  private long rejectedRows;
  private Instant queuedAt;
  private Instant startedAt;
  private Instant finishedAt;
  private Instant updatedAt;
  private String failureReason;

  private IngestionJob(UUID id, String originalFilename, Instant createdAt) {
    this.id = Objects.requireNonNull(id, "O identificador do job é obrigatório.");
    this.originalFilename = requireText(originalFilename, "O nome original do arquivo é obrigatório.");
    this.createdAt = Objects.requireNonNull(createdAt, "A data de criação é obrigatória.");
    this.status = IngestionStatus.RECEIVED;
    this.updatedAt = createdAt;
  }

  public static IngestionJob receive(UUID id, String originalFilename, Instant receivedAt) {
    return new IngestionJob(id, originalFilename, receivedAt);
  }

  public void markQueued(Instant occurredAt) {
    requireStatus(IngestionStatus.RECEIVED, IngestionStatus.QUEUED);
    queuedAt = requireInstant(occurredAt);
    status = IngestionStatus.QUEUED;
    updatedAt = occurredAt;
  }

  public void startProcessing(Instant occurredAt) {
    requireInstant(occurredAt);

    // O redelivery pode reenviar um job que já iniciou; repetir esta transição deve ser seguro.
    if (status == IngestionStatus.PROCESSING) {
      return;
    }

    requireStatus(IngestionStatus.QUEUED, IngestionStatus.PROCESSING);
    startedAt = occurredAt;
    status = IngestionStatus.PROCESSING;
    updatedAt = occurredAt;
  }

  public void recordBatch(long acceptedInBatch, long rejectedInBatch, Instant occurredAt) {
    requireStatus(IngestionStatus.PROCESSING, IngestionStatus.PROCESSING);
    requireInstant(occurredAt);
    requireBatchCounts(acceptedInBatch, rejectedInBatch);

    // Todos os cálculos ocorrem antes da alteração para evitar contadores parciais em um estouro aritmético.
    long nextAcceptedRows = Math.addExact(acceptedRows, acceptedInBatch);
    long nextRejectedRows = Math.addExact(rejectedRows, rejectedInBatch);
    Math.addExact(nextAcceptedRows, nextRejectedRows);

    acceptedRows = nextAcceptedRows;
    rejectedRows = nextRejectedRows;
    updatedAt = occurredAt;
  }

  public void complete(Instant occurredAt) {
    IngestionStatus targetStatus = rejectedRows == 0
        ? IngestionStatus.COMPLETED
        : IngestionStatus.COMPLETED_WITH_ERRORS;

    requireStatus(IngestionStatus.PROCESSING, targetStatus);
    finishedAt = requireInstant(occurredAt);
    status = targetStatus;
    updatedAt = occurredAt;
  }

  public void fail(String reason, Instant occurredAt) {
    if (status.isTerminal()) {
      throw new InvalidIngestionJobTransitionException(status, IngestionStatus.FAILED);
    }

    String validatedReason = requireText(reason, "O motivo da falha é obrigatório.");
    Instant failedAt = requireInstant(occurredAt);

    failureReason = validatedReason;
    finishedAt = failedAt;
    status = IngestionStatus.FAILED;
    updatedAt = failedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public IngestionStatus getStatus() {
    return status;
  }

  public long getProcessedRows() {
    return Math.addExact(acceptedRows, rejectedRows);
  }

  public long getAcceptedRows() {
    return acceptedRows;
  }

  public long getRejectedRows() {
    return rejectedRows;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Optional<Instant> getQueuedAt() {
    return Optional.ofNullable(queuedAt);
  }

  public Optional<Instant> getStartedAt() {
    return Optional.ofNullable(startedAt);
  }

  public Optional<Instant> getFinishedAt() {
    return Optional.ofNullable(finishedAt);
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Optional<String> getFailureReason() {
    return Optional.ofNullable(failureReason);
  }

  private void requireStatus(IngestionStatus expected, IngestionStatus target) {
    if (status != expected) {
      throw new InvalidIngestionJobTransitionException(status, target);
    }
  }

  private static void requireBatchCounts(long accepted, long rejected) {
    if (accepted < 0 || rejected < 0) {
      throw new IllegalArgumentException("Os contadores do lote não podem ser negativos.");
    }
    if (accepted == 0 && rejected == 0) {
      throw new IllegalArgumentException("O lote deve conter ao menos uma linha.");
    }
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private static Instant requireInstant(Instant value) {
    return Objects.requireNonNull(value, "A data da transição é obrigatória.");
  }
}
