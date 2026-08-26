package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vicx074.geosapiens.ingestion.application.GetIngestionAnalytics;
import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics;
import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics.CategoryAggregate;
import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics.MonthAggregate;
import io.github.vicx074.geosapiens.ingestion.application.IngestionJobNotFoundException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IngestionAnalyticsControllerTest {

  private static final UUID JOB_ID = UUID.fromString("ab930ec3-0821-4fc7-b6ca-acd4161a45af");

  @Test
  void shouldExposeDashboardAggregatesWithBoundedStructure() throws Exception {
    GetIngestionAnalytics getAnalytics = mock(GetIngestionAnalytics.class);
    when(getAnalytics.execute(JOB_ID)).thenReturn(new IngestionAnalytics(
        3,
        new BigDecimal("124.75"),
        List.of(
            new CategoryAggregate("alimentação", 2, new BigDecimal("74.50")),
            new CategoryAggregate("transporte", 1, new BigDecimal("50.25"))),
        List.of(
            new MonthAggregate(YearMonth.of(2025, 1), 2, new BigDecimal("74.50")),
            new MonthAggregate(YearMonth.of(2025, 2), 1, new BigDecimal("50.25")))));

    mvc(getAnalytics).perform(get("/imports/{jobId}/analytics", JOB_ID))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(jsonPath("$.transactionCount").value(3))
        .andExpect(jsonPath("$.totalAmount").value(124.75))
        .andExpect(jsonPath("$.byCategory.length()").value(2))
        .andExpect(jsonPath("$.byCategory[0].category").value("alimentação"))
        .andExpect(jsonPath("$.byCategory[0].transactionCount").value(2))
        .andExpect(jsonPath("$.byMonth.length()").value(2))
        .andExpect(jsonPath("$.byMonth[0].month").value("2025-01"));
  }

  @Test
  void shouldReturnProblemDetailWhenImportDoesNotExist() throws Exception {
    GetIngestionAnalytics getAnalytics = mock(GetIngestionAnalytics.class);
    when(getAnalytics.execute(JOB_ID)).thenThrow(new IngestionJobNotFoundException(JOB_ID));

    mvc(getAnalytics).perform(get("/imports/{jobId}/analytics", JOB_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Importação não encontrada"));
  }

  private static MockMvc mvc(GetIngestionAnalytics getAnalytics) {
    return MockMvcBuilders
        .standaloneSetup(new IngestionAnalyticsController(getAnalytics))
        .setControllerAdvice(new IngestionExceptionHandler())
        .build();
  }
}
