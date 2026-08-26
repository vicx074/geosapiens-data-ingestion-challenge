package io.github.vicx074.geosapiens.ingestion.application.port.out;

import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.util.Optional;
import java.util.UUID;

public interface IngestionJobRepository {

  void insert(IngestionJob job);

  Optional<VersionedIngestionJob> findById(UUID id);

  VersionedIngestionJob update(VersionedIngestionJob versionedJob);
}
