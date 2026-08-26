package io.github.vicx074.geosapiens.ingestion.application;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.VersionedIngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class GetIngestionStatus {

  private final IngestionJobRepository jobs;

  public GetIngestionStatus(IngestionJobRepository jobs) {
    this.jobs = jobs;
  }

  public IngestionJob execute(UUID jobId) {
    Objects.requireNonNull(jobId, "O identificador da importação é obrigatório.");
    return jobs.findById(jobId)
        .map(VersionedIngestionJob::job)
        .orElseThrow(() -> new IngestionJobNotFoundException(jobId));
  }
}
