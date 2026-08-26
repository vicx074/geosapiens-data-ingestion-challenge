package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.util.UUID;

public interface JobQueuePublisher {

  void publish(UUID jobId);
}
