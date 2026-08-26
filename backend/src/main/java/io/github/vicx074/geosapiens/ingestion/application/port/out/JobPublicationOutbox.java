package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobPublicationOutbox {

  void insert(PendingJobPublication publication);

  List<ClaimedJobPublication> claimAvailable(
      Instant now, Instant claimedUntil, int limit, UUID claimToken);

  void markPublished(UUID jobId, UUID claimToken, Instant publishedAt);

  void reschedule(
      UUID jobId, UUID claimToken, Instant availableAt, String lastError);

  void markFailed(UUID jobId, UUID claimToken, Instant failedAt, String lastError);
}
