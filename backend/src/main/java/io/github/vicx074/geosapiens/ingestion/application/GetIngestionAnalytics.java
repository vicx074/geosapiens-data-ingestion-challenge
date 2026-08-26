package io.github.vicx074.geosapiens.ingestion.application;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionAnalyticsQuery;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class GetIngestionAnalytics {

  private final IngestionJobRepository jobs;
  private final IngestionAnalyticsQuery analytics;

  public GetIngestionAnalytics(IngestionJobRepository jobs, IngestionAnalyticsQuery analytics) {
    this.jobs = jobs;
    this.analytics = analytics;
  }

  public IngestionAnalytics execute(UUID jobId) {
    Objects.requireNonNull(jobId, "O identificador da importação é obrigatório.");
    if (jobs.findById(jobId).isEmpty()) {
      throw new IngestionJobNotFoundException(jobId);
    }
    return analytics.fetch(jobId);
  }
}
