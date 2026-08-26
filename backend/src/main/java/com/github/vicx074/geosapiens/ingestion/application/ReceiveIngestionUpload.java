package com.github.vicx074.geosapiens.ingestion.application;

import com.github.vicx074.geosapiens.ingestion.application.port.out.StoredTemporaryFile;
import com.github.vicx074.geosapiens.ingestion.application.port.out.TemporaryFileStorage;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ReceiveIngestionUpload {

  private final TemporaryFileStorage storage;
  private final RegisterIngestionJob registerJob;
  private final Clock clock;

  public ReceiveIngestionUpload(
      TemporaryFileStorage storage,
      RegisterIngestionJob registerJob,
      Clock clock) {
    this.storage = storage;
    this.registerJob = registerJob;
    this.clock = clock;
  }

  public IngestionJob execute(String originalFilename, InputStream content) throws IOException {
    String validatedFilename = validateFilename(originalFilename);
    Objects.requireNonNull(content, "O conteúdo do arquivo é obrigatório.");

    UUID jobId = UUID.randomUUID();
    Instant receivedAt = clock.instant();
    StoredTemporaryFile storedFile = storage.store(jobId, content);

    if (storedFile.sizeInBytes() == 0) {
      InvalidIngestionUploadException exception = new InvalidIngestionUploadException(
          "O arquivo CSV não pode estar vazio."
      );
      deleteStoredFile(storedFile.storageKey(), exception);
      throw exception;
    }

    try {
      return registerJob.execute(jobId, validatedFilename, receivedAt);
    } catch (RuntimeException exception) {
      deleteStoredFile(storedFile.storageKey(), exception);
      throw exception;
    }
  }

  private static String validateFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new InvalidIngestionUploadException("O nome do arquivo é obrigatório.");
    }

    String normalizedFilename = originalFilename.trim();
    if (!normalizedFilename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new InvalidIngestionUploadException("O arquivo enviado deve possuir extensão .csv.");
    }
    return normalizedFilename;
  }

  private void deleteStoredFile(String storageKey, Exception originalException) {
    try {
      storage.delete(storageKey);
    } catch (IOException cleanupException) {
      originalException.addSuppressed(cleanupException);
    }
  }
}
