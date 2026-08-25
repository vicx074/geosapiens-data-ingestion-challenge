package com.github.vicx074.geosapiens.ingestion.application.port.out;

import com.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.util.Objects;

/**
 * Mantém a versão técnica fora do agregado e permite detectar atualizações concorrentes.
 */
public record VersionedIngestionJob(IngestionJob job, long version) {

  public VersionedIngestionJob {
    Objects.requireNonNull(job, "O job versionado é obrigatório.");
    if (version < 0) {
      throw new IllegalArgumentException("A versão do job não pode ser negativa.");
    }
  }
}
