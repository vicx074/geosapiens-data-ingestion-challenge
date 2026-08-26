package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vicx074.geosapiens.ingestion.application.GetIngestionStatus;
import io.github.vicx074.geosapiens.ingestion.application.IngestionJobNotFoundException;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IngestionStatusControllerTest {

  private static final UUID JOB_ID = UUID.fromString("b6e891dd-0873-4a5c-9024-54ffc20ef9db");
  private static final Instant RECEIVED_AT = Instant.parse("2026-08-26T12:00:00Z");

  @Test
  void shouldExposeCommittedProgressAsBoundedPollingResponse() throws Exception {
    GetIngestionStatus getStatus = mock(GetIngestionStatus.class);
    IngestionJob job = IngestionJob.receive(JOB_ID, "transactions.csv", RECEIVED_AT);
    job.markQueued(RECEIVED_AT.plusSeconds(1));
    job.startProcessing(RECEIVED_AT.plusSeconds(2));
    job.recordBatch(950, 50, RECEIVED_AT.plusSeconds(3));
    when(getStatus.execute(JOB_ID)).thenReturn(job);
    MockMvc mvc = mvc(getStatus);

    mvc.perform(get("/imports/{jobId}", JOB_ID))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(jsonPath("$.jobId").value(JOB_ID.toString()))
        .andExpect(jsonPath("$.filename").value("transactions.csv"))
        .andExpect(jsonPath("$.status").value("PROCESSING"))
        .andExpect(jsonPath("$.processedRows").value(1000))
        .andExpect(jsonPath("$.acceptedRows").value(950))
        .andExpect(jsonPath("$.rejectedRows").value(50))
        .andExpect(jsonPath("$.terminal").value(false))
        .andExpect(jsonPath("$.createdAt").value(RECEIVED_AT.toString()))
        .andExpect(jsonPath("$.queuedAt").value(RECEIVED_AT.plusSeconds(1).toString()))
        .andExpect(jsonPath("$.startedAt").value(RECEIVED_AT.plusSeconds(2).toString()))
        .andExpect(jsonPath("$.finishedAt").doesNotExist())
        .andExpect(jsonPath("$.updatedAt").value(RECEIVED_AT.plusSeconds(3).toString()))
        .andExpect(jsonPath("$.failureReason").doesNotExist());
  }

  @Test
  void shouldExposeFailureWithoutHidingTheReason() throws Exception {
    GetIngestionStatus getStatus = mock(GetIngestionStatus.class);
    IngestionJob job = IngestionJob.receive(JOB_ID, "transactions.csv", RECEIVED_AT);
    job.markQueued(RECEIVED_AT.plusSeconds(1));
    job.fail("Cabeçalho CSV inválido", RECEIVED_AT.plusSeconds(2));
    when(getStatus.execute(JOB_ID)).thenReturn(job);

    mvc(getStatus).perform(get("/imports/{jobId}", JOB_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.terminal").value(true))
        .andExpect(jsonPath("$.failureReason").value("Cabeçalho CSV inválido"))
        .andExpect(jsonPath("$.finishedAt").value(RECEIVED_AT.plusSeconds(2).toString()));
  }

  @Test
  void shouldReturnProblemDetailWhenImportDoesNotExist() throws Exception {
    GetIngestionStatus getStatus = mock(GetIngestionStatus.class);
    when(getStatus.execute(JOB_ID)).thenThrow(new IngestionJobNotFoundException(JOB_ID));

    mvc(getStatus).perform(get("/imports/{jobId}", JOB_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Importação não encontrada"))
        .andExpect(jsonPath("$.detail").value(containsString(JOB_ID.toString())));
  }

  private static MockMvc mvc(GetIngestionStatus getStatus) {
    return MockMvcBuilders
        .standaloneSetup(new IngestionStatusController(getStatus))
        .setControllerAdvice(new IngestionExceptionHandler())
        .build();
  }
}
