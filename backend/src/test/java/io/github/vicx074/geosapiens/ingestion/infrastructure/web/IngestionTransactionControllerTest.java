package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vicx074.geosapiens.ingestion.application.IngestionTransactionPage;
import io.github.vicx074.geosapiens.ingestion.application.IngestionTransactionRecord;
import io.github.vicx074.geosapiens.ingestion.application.InvalidIngestionPaginationException;
import io.github.vicx074.geosapiens.ingestion.application.ListIngestionTransactions;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IngestionTransactionControllerTest {

  private static final UUID JOB_ID = UUID.fromString("b908c7cc-5e85-4a5c-b31c-bd6bcf63839e");

  @Test
  void shouldExposeBoundedTransactionPageWithNextCursor() throws Exception {
    ListIngestionTransactions listTransactions = mock(ListIngestionTransactions.class);
    when(listTransactions.execute(JOB_ID, 2, 100L)).thenReturn(new IngestionTransactionPage(
        List.of(
            transaction(101, 8, "txn-101", "10.50", "transporte"),
            transaction(102, 13, "txn-102", "20.25", "saúde")),
        102L));

    mvc(listTransactions).perform(get("/imports/{jobId}/transactions", JOB_ID)
            .param("limit", "2")
            .param("after", "100"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value(101))
        .andExpect(jsonPath("$.items[0].sourceRow").value(8))
        .andExpect(jsonPath("$.items[0].transactionId").value("txn-101"))
        .andExpect(jsonPath("$.items[0].occurredAt").value("2025-01-02T03:04:05Z"))
        .andExpect(jsonPath("$.items[0].amount").value(10.50))
        .andExpect(jsonPath("$.items[0].category").value("transporte"))
        .andExpect(jsonPath("$.nextCursor").value(102));
  }

  @Test
  void shouldUseDefaultPageSizeWhenLimitIsOmitted() throws Exception {
    ListIngestionTransactions listTransactions = mock(ListIngestionTransactions.class);
    when(listTransactions.execute(JOB_ID, ListIngestionTransactions.DEFAULT_PAGE_SIZE, null))
        .thenReturn(new IngestionTransactionPage(List.of(), null));

    mvc(listTransactions).perform(get("/imports/{jobId}/transactions", JOB_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void shouldReturnProblemDetailForInvalidPagination() throws Exception {
    ListIngestionTransactions listTransactions = mock(ListIngestionTransactions.class);
    when(listTransactions.execute(JOB_ID, 500, null)).thenThrow(
        new InvalidIngestionPaginationException("O limite da página deve estar entre 1 e 200."));

    mvc(listTransactions).perform(
            get("/imports/{jobId}/transactions", JOB_ID).param("limit", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Paginação inválida"))
        .andExpect(jsonPath("$.detail").value(containsString("entre 1 e 200")));
  }

  private static IngestionTransactionRecord transaction(
      long id,
      long sourceRow,
      String transactionId,
      String amount,
      String category) {
    return new IngestionTransactionRecord(
        id,
        sourceRow,
        transactionId,
        Instant.parse("2025-01-02T03:04:05Z"),
        new BigDecimal(amount),
        category);
  }

  private static MockMvc mvc(ListIngestionTransactions listTransactions) {
    return MockMvcBuilders
        .standaloneSetup(new IngestionTransactionController(listTransactions))
        .setControllerAdvice(new IngestionExceptionHandler())
        .build();
  }
}
