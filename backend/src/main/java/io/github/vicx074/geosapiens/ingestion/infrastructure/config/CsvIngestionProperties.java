package io.github.vicx074.geosapiens.ingestion.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.csv")
public record CsvIngestionProperties(int maxRecordCharacters) {

  public CsvIngestionProperties {
    if (maxRecordCharacters < 512 || maxRecordCharacters > 1_000_000) {
      throw new IllegalArgumentException(
          "O limite de caracteres por registro CSV deve estar entre 512 e 1000000.");
    }
  }
}
