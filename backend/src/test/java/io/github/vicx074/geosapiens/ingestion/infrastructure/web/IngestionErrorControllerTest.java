package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vicx074.geosapiens.ingestion.application.IngestionErrorPage;
import io.github.vicx074.geosapiens.ingestion.application.IngestionErrorRecord;
import io.github.vicx074.geosapiens.ingestion.application.InvalidIngestionPaginationException;
import io.github.vicx074.geosapiens.ingestion.application.ListIngestionErrors;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IngestionErrorControllerTest {

  private static final UUID JOB_ID = UUID.fromString("2820f65e-71c7-4fa1-9173-68dd37c99d1b");

  @Test
  void shouldExposeBoundedErrorPageWithNextCursor() throws Exception {
    ListIngestionErrors listErrors = mock(ListIngestionErrors.class);
    when(listErrors.execute(JOB_ID, 2, 5L)).thenReturn(new IngestionErrorPage(
        List.of(
            new IngestionErrorRecord(8, "AMOUNT_ZERO", "amount não pode ser zero."),
            new IngestionErrorRecord(13, "CATEGORY_REQUIRED", "category é obrigatório.")),
        13L));

    mvc(listErrors).perform(get("/imports/{jobId}/errors", JOB_ID)
            .param("limit", "2")
            .param("after", "5"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].sourceRow").value(8))
        .andExpect(jsonPath("$.items[0].code").value("AMOUNT_ZERO"))
        .andExpect(jsonPath("$.items[1].sourceRow").value(13))
        .andExpect(jsonPath("$.nextCursor").value(13));
  }

  @Test
  void shouldUseDefaultPageSizeWhenLimitIsOmitted() throws Exception {
    ListIngestionErrors listErrors = mock(ListIngestionErrors.class);
    when(listErrors.execute(JOB_ID, ListIngestionErrors.DEFAULT_PAGE_SIZE, null))
        .thenReturn(new IngestionErrorPage(List.of(), null));

    mvc(listErrors).perform(get("/imports/{jobId}/errors", JOB_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void shouldReturnProblemDetailForInvalidPagination() throws Exception {
    ListIngestionErrors listErrors = mock(ListIngestionErrors.class);
    when(listErrors.execute(JOB_ID, 500, null)).thenThrow(
        new InvalidIngestionPaginationException("O limite da página deve estar entre 1 e 200."));

    mvc(listErrors).perform(get("/imports/{jobId}/errors", JOB_ID).param("limit", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Paginação inválida"))
        .andExpect(jsonPath("$.detail").value(containsString("entre 1 e 200")));
  }

  private static MockMvc mvc(ListIngestionErrors listErrors) {
    return MockMvcBuilders
        .standaloneSetup(new IngestionErrorController(listErrors))
        .setControllerAdvice(new IngestionExceptionHandler())
        .build();
  }
}
