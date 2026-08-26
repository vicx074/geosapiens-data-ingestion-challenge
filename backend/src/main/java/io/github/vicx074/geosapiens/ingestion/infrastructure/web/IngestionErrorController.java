package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import io.github.vicx074.geosapiens.ingestion.application.IngestionErrorPage;
import io.github.vicx074.geosapiens.ingestion.application.IngestionErrorRecord;
import io.github.vicx074.geosapiens.ingestion.application.ListIngestionErrors;
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
public class IngestionErrorController {

  private final ListIngestionErrors listErrors;

  public IngestionErrorController(ListIngestionErrors listErrors) {
    this.listErrors = listErrors;
  }

  @GetMapping("/{jobId}/errors")
  ResponseEntity<IngestionErrorPageResponse> errors(
      @PathVariable UUID jobId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) Long after) {
    IngestionErrorPage page = listErrors.execute(jobId, limit, after);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(IngestionErrorPageResponse.from(page));
  }

  public record IngestionErrorPageResponse(
      List<IngestionErrorItemResponse> items,
      Long nextCursor) {

    static IngestionErrorPageResponse from(IngestionErrorPage page) {
      List<IngestionErrorItemResponse> items = page.items().stream()
          .map(IngestionErrorItemResponse::from)
          .toList();
      return new IngestionErrorPageResponse(items, page.nextCursor());
    }
  }

  public record IngestionErrorItemResponse(long sourceRow, String code, String reason) {

    static IngestionErrorItemResponse from(IngestionErrorRecord error) {
      return new IngestionErrorItemResponse(error.sourceRow(), error.code(), error.reason());
    }
  }
}
