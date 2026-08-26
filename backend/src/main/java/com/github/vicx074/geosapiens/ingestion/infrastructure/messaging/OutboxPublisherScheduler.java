package com.github.vicx074.geosapiens.ingestion.infrastructure.messaging;

import com.github.vicx074.geosapiens.ingestion.application.PublishPendingIngestionJobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true")
public final class OutboxPublisherScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

  private final PublishPendingIngestionJobs publisher;

  public OutboxPublisherScheduler(PublishPendingIngestionJobs publisher) {
    this.publisher = publisher;
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
  public void publishAvailableJobs() {
    int publishedJobs = publisher.execute();
    if (publishedJobs > 0) {
      LOGGER.atInfo()
          .addKeyValue("publishedJobs", publishedJobs)
          .log("Lote do Outbox processado.");
    }
  }
}
