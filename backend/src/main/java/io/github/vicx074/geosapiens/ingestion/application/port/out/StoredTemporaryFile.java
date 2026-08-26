package io.github.vicx074.geosapiens.ingestion.application.port.out;

public record StoredTemporaryFile(String storageKey, long sizeInBytes) {

  public StoredTemporaryFile {
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("A chave de armazenamento é obrigatória.");
    }
    if (sizeInBytes < 0) {
      throw new IllegalArgumentException("O tamanho armazenado não pode ser negativo.");
    }
  }
}
