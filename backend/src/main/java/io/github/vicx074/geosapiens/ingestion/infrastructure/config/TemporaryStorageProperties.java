package io.github.vicx074.geosapiens.ingestion.infrastructure.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage")
public record TemporaryStorageProperties(Path temporaryDirectory) {

  public TemporaryStorageProperties {
    if (temporaryDirectory == null) {
      throw new IllegalArgumentException("O diretório temporário é obrigatório.");
    }
  }
}
