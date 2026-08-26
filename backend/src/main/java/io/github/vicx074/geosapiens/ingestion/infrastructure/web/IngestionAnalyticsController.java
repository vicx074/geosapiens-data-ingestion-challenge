package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import io.github.vicx074.geosapiens.ingestion.application.GetIngestionAnalytics;
import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/imports")
public class IngestionAnalyticsController {

  private final GetIngestionAnalytics getAnalytics;

  public IngestionAnalyticsController(GetIngestionAnalytics getAnalytics) {
    this.getAnalytics = getAnalytics;
  }

  @GetMapping("/{jobId}/analytics")
  ResponseEntity<IngestionAnalyticsResponse> analytics(@PathVariable UUID jobId) {
    IngestionAnalytics result = getAnalytics.execute(jobId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(IngestionAnalyticsResponse.from(result));
  }

  public record IngestionAnalyticsResponse(
      long transactionCount,
      BigDecimal totalAmount,
      List<CategoryAggregateResponse> byCategory,
      List<MonthAggregateResponse> byMonth) {

    static IngestionAnalyticsResponse from(IngestionAnalytics analytics) {
      return new IngestionAnalyticsResponse(
          analytics.transactionCount(),
          analytics.totalAmount(),
          analytics.byCategory().stream().map(CategoryAggregateResponse::from).toList(),
          analytics.byMonth().stream().map(MonthAggregateResponse::from).toList());
    }
  }

  public record CategoryAggregateResponse(
      String category,
      long transactionCount,
      BigDecimal totalAmount) {

    static CategoryAggregateResponse from(IngestionAnalytics.CategoryAggregate aggregate) {
      return new CategoryAggregateResponse(
          aggregate.category(), aggregate.transactionCount(), aggregate.totalAmount());
    }
  }

  public record MonthAggregateResponse(
      String month,
      long transactionCount,
      BigDecimal totalAmount) {

    static MonthAggregateResponse from(IngestionAnalytics.MonthAggregate aggregate) {
      return new MonthAggregateResponse(
          aggregate.month().toString(), aggregate.transactionCount(), aggregate.totalAmount());
    }
  }
}
