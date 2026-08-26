package io.github.vicx074.geosapiens.ingestion.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.worker")
public record WorkerProperties(boolean enabled, int concurrency, int prefetch) {

  public WorkerProperties {
    if (concurrency < 1 || concurrency > 32) {
      throw new IllegalArgumentException("A concorrência do Worker deve estar entre 1 e 32.");
    }
    if (prefetch < 1 || prefetch > 128) {
      throw new IllegalArgumentException("O prefetch do Worker deve estar entre 1 e 128.");
    }
  }
}
