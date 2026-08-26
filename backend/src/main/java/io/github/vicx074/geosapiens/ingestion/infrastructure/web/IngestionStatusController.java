package io.github.vicx074.geosapiens.ingestion.infrastructure.web;

import io.github.vicx074.geosapiens.ingestion.application.GetIngestionStatus;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/imports")
public class IngestionStatusController {

  private final GetIngestionStatus getStatus;

  public IngestionStatusController(GetIngestionStatus getStatus) {
    this.getStatus = getStatus;
  }

  @GetMapping("/{jobId}")
  ResponseEntity<IngestionStatusResponse> status(@PathVariable UUID jobId) {
    IngestionJob job = getStatus.execute(jobId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(IngestionStatusResponse.from(job));
  }

  public record IngestionStatusResponse(
      UUID jobId,
      String filename,
      String status,
      long processedRows,
      long acceptedRows,
      long rejectedRows,
      boolean terminal,
      Instant createdAt,
      Instant queuedAt,
      Instant startedAt,
      Instant finishedAt,
      Instant updatedAt,
      String failureReason) {

    static IngestionStatusResponse from(IngestionJob job) {
      return new IngestionStatusResponse(
          job.getId(),
          job.getOriginalFilename(),
          job.getStatus().name(),
          job.getProcessedRows(),
          job.getAcceptedRows(),
          job.getRejectedRows(),
          job.getStatus().isTerminal(),
          job.getCreatedAt(),
          job.getQueuedAt().orElse(null),
          job.getStartedAt().orElse(null),
          job.getFinishedAt().orElse(null),
          job.getUpdatedAt(),
          job.getFailureReason().orElse(null));
    }
  }
}
