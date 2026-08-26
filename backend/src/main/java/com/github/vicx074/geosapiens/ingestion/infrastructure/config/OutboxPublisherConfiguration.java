package com.github.vicx074.geosapiens.ingestion.infrastructure.config;

import com.github.vicx074.geosapiens.ingestion.application.OutboxPublicationPolicy;
import com.github.vicx074.geosapiens.ingestion.application.PublishPendingIngestionJobs;
import com.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobPublicationOutbox;
import com.github.vicx074.geosapiens.ingestion.application.port.out.JobQueuePublisher;
import com.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class OutboxPublisherConfiguration {

  @Bean
  Clock systemClock() {
    return Clock.systemUTC();
  }

  @Bean
  OutboxPublicationPolicy outboxPublicationPolicy(OutboxPublisherProperties properties) {
    return properties.publicationPolicy();
  }

  @Bean
  @ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true")
  PublishPendingIngestionJobs publishPendingIngestionJobs(
      JobPublicationOutbox outbox,
      JobQueuePublisher publisher,
      IngestionJobRepository jobs,
      TransactionRunner transactions,
      OutboxPublicationPolicy policy,
      Clock clock) {
    return new PublishPendingIngestionJobs(
        outbox, publisher, jobs, transactions, policy, clock);
  }
}
