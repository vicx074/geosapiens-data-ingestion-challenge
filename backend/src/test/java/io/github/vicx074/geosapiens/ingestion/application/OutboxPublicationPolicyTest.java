package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxPublicationPolicyTest {

  @Test
  void shouldApplyBoundedExponentialRetryDelay() {
    OutboxPublicationPolicy policy = policy();

    assertThat(policy.retryDelay(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(policy.retryDelay(2)).isEqualTo(Duration.ofSeconds(2));
    assertThat(policy.retryDelay(3)).isEqualTo(Duration.ofSeconds(4));
    assertThat(policy.retryDelay(4)).isEqualTo(Duration.ofSeconds(5));
    assertThat(policy.retryDelay(20)).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void shouldRejectClaimShorterThanWorstBatchConfirmationTime() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxPublicationPolicy(
            10,
            Duration.ofSeconds(50),
            Duration.ofSeconds(5),
            3,
            Duration.ofSeconds(1),
            Duration.ofSeconds(5)))
        .withMessage("A reivindicação deve durar mais que o pior tempo de confirmação do lote.");
  }

  private static OutboxPublicationPolicy policy() {
    return new OutboxPublicationPolicy(
        10,
        Duration.ofSeconds(51),
        Duration.ofSeconds(5),
        3,
        Duration.ofSeconds(1),
        Duration.ofSeconds(5));
  }
}
