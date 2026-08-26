package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetIngestionStatusTest {

  private static final UUID JOB_ID = UUID.fromString("b6e891dd-0873-4a5c-9024-54ffc20ef9db");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-26T12:00:00Z");

  @Test
  void shouldReturnPersistedJobWithoutChangingItsState() {
    IngestionJobRepository jobs = mock(IngestionJobRepository.class);
    IngestionJob job = IngestionJob.receive(JOB_ID, "transactions.csv", RECEIVED_AT);
    job.markQueued(RECEIVED_AT.plusSeconds(1));
    job.startProcessing(RECEIVED_AT.plusSeconds(2));
    job.recordBatch(950, 50, RECEIVED_AT.plusSeconds(3));
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(new VersionedIngestionJob(job, 7)));

    IngestionJob result = new GetIngestionStatus(jobs).execute(JOB_ID);

    assertThat(result).isSameAs(job);
    assertThat(result.getProcessedRows()).isEqualTo(1000);
    assertThat(result.getAcceptedRows()).isEqualTo(950);
    assertThat(result.getRejectedRows()).isEqualTo(50);
  }

  @Test
  void shouldFailWhenImportDoesNotExist() {
    IngestionJobRepository jobs = mock(IngestionJobRepository.class);
    when(jobs.findById(JOB_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new GetIngestionStatus(jobs).execute(JOB_ID))
        .isInstanceOf(IngestionJobNotFoundException.class)
        .hasMessageContaining(JOB_ID.toString());
  }
}
