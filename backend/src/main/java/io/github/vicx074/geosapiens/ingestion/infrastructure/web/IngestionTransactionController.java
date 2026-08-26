package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import io.github.vicx074.geosapiens.ingestion.application.IngestionTransactionPage;
import io.github.vicx074.geosapiens.ingestion.application.IngestionTransactionRecord;
import io.github.vicx074.geosapiens.ingestion.application.ListIngestionTransactions;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/imports")
public class IngestionTransactionController {

  private final ListIngestionTransactions listTransactions;

  public IngestionTransactionController(ListIngestionTransactions listTransactions) {
    this.listTransactions = listTransactions;
  }

  @GetMapping("/{jobId}/transactions")
  ResponseEntity<IngestionTransactionPageResponse> transactions(
      @PathVariable UUID jobId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) Long after) {
    IngestionTransactionPage page = listTransactions.execute(jobId, limit, after);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(IngestionTransactionPageResponse.from(page));
  }

  public record IngestionTransactionPageResponse(
      List<IngestionTransactionItemResponse> items,
      Long nextCursor) {

    static IngestionTransactionPageResponse from(IngestionTransactionPage page) {
      List<IngestionTransactionItemResponse> items = page.items().stream()
          .map(IngestionTransactionItemResponse::from)
          .toList();
      return new IngestionTransactionPageResponse(items, page.nextCursor());
    }
  }

  public record IngestionTransactionItemResponse(
      long id,
      long sourceRow,
      String transactionId,
      String occurredAt,
      BigDecimal amount,
      String category) {

    static IngestionTransactionItemResponse from(IngestionTransactionRecord transaction) {
      return new IngestionTransactionItemResponse(
          transaction.id(),
          transaction.sourceRow(),
          transaction.transactionId(),
          transaction.occurredAt().toString(),
          transaction.amount(),
          transaction.category());
    }
  }
}
