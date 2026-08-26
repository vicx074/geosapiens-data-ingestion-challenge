package io.github.vicx074.geosapiens.ingestion.infrastructure.config;

import io.github.vicx074.geosapiens.ingestion.application.port.out.TemporaryFileStorage;
import io.github.vicx074.geosapiens.ingestion.infrastructure.storage.FileSystemTemporaryFileStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TemporaryStorageConfiguration {

  @Bean
  TemporaryFileStorage temporaryFileStorage(TemporaryStorageProperties properties) {
    return new FileSystemTemporaryFileStorage(properties.temporaryDirectory());
  }
}
