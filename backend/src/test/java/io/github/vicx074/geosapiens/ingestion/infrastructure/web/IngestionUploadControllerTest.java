package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vicx074.geosapiens.ingestion.application.ReceiveIngestionUpload;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class IngestionUploadControllerTest {

  private static final UUID JOB_ID = UUID.fromString("cbb9f567-3ae5-4f41-87b0-221a2f436eca");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-25T12:00:00Z");

  @Test
  void shouldReturnAcceptedOnlyAfterUseCaseCompletes() throws Exception {
    ReceiveIngestionUpload receiveUpload = mock(ReceiveIngestionUpload.class);
    IngestionJob job = IngestionJob.receive(JOB_ID, "transactions.csv", RECEIVED_AT);
    job.markQueued(RECEIVED_AT);
    when(receiveUpload.execute(org.mockito.ArgumentMatchers.eq("transactions.csv"),
        org.mockito.ArgumentMatchers.any(InputStream.class))).thenReturn(job);
    IngestionUploadController controller = new IngestionUploadController(receiveUpload);
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "transactions.csv",
        "text/csv",
        "id,amount\n1,10.00".getBytes()
    );

    var response = controller.upload(file);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).hasToString("/imports/" + JOB_ID);
    assertThat(response.getBody()).isNotNull().satisfies(body -> {
      assertThat(body.jobId()).isEqualTo(JOB_ID);
      assertThat(body.status()).isEqualTo("QUEUED");
      assertThat(body.statusUrl()).hasToString("/imports/" + JOB_ID);
    });
  }
}
