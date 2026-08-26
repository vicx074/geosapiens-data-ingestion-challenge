package com.github.vicx074.geosapiens.ingestion.application;

import java.time.Duration;
import java.util.Objects;

public record OutboxPublicationPolicy(
    int batchSize,
    Duration claimDuration,
    Duration confirmTimeout,
    int maxAttempts,
    Duration initialRetryDelay,
    Duration maxRetryDelay) {

  public OutboxPublicationPolicy {
    requirePositive(batchSize, "O lote do Outbox deve ser positivo.");
    requirePositive(maxAttempts, "A quantidade máxima de tentativas deve ser positiva.");
    requirePositive(claimDuration, "A duração da reivindicação deve ser positiva.");
    requirePositive(confirmTimeout, "O timeout de confirmação deve ser positivo.");
    requirePositive(initialRetryDelay, "O intervalo inicial de retry deve ser positivo.");
    requirePositive(maxRetryDelay, "O intervalo máximo de retry deve ser positivo.");

    if (initialRetryDelay.compareTo(maxRetryDelay) > 0) {
      throw new IllegalArgumentException(
          "O intervalo inicial de retry não pode superar o intervalo máximo.");
    }
    if (claimDuration.compareTo(confirmTimeout.multipliedBy(batchSize)) <= 0) {
      throw new IllegalArgumentException(
          "A reivindicação deve durar mais que o pior tempo de confirmação do lote.");
    }
  }

  public Duration retryDelay(int attempts) {
    requirePositive(attempts, "A tentativa deve ser positiva.");

    Duration delay = initialRetryDelay;
    for (int currentAttempt = 1;
         currentAttempt < attempts && delay.compareTo(maxRetryDelay) < 0;
         currentAttempt++) {
      Duration doubled = delay.multipliedBy(2);
      delay = doubled.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : doubled;
    }
    return delay;
  }

  private static void requirePositive(int value, String message) {
    if (value <= 0) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requirePositive(Duration value, String message) {
    Objects.requireNonNull(value, message);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(message);
    }
  }
}
