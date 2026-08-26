package com.github.vicx074.geosapiens.ingestion.infrastructure.storage;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.WRITE;

import com.github.vicx074.geosapiens.ingestion.application.port.out.StoredTemporaryFile;
import com.github.vicx074.geosapiens.ingestion.application.port.out.TemporaryFileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public final class FileSystemTemporaryFileStorage implements TemporaryFileStorage {

  static final int COPY_BUFFER_SIZE_IN_BYTES = 64 * 1024;

  private final Path baseDirectory;

  public FileSystemTemporaryFileStorage(Path baseDirectory) {
    this.baseDirectory = Objects.requireNonNull(
        baseDirectory,
        "O diretório de armazenamento é obrigatório."
    ).toAbsolutePath().normalize();
  }

  @Override
  public StoredTemporaryFile store(UUID jobId, InputStream content) throws IOException {
    Objects.requireNonNull(jobId, "O identificador do job é obrigatório.");
    Objects.requireNonNull(content, "O conteúdo do arquivo é obrigatório.");

    Files.createDirectories(baseDirectory);

    String storageKey = jobId + ".csv";
    Path finalFile = baseDirectory.resolve(storageKey);
    ensureFinalFileDoesNotExist(finalFile);

    Path partialFile = Files.createTempFile(baseDirectory, jobId + ".", ".part");
    try {
      long storedBytes = copyWithBoundedMemory(content, partialFile);

      // O Worker nunca deve observar um arquivo parcialmente gravado.
      Files.move(partialFile, finalFile, ATOMIC_MOVE);
      return new StoredTemporaryFile(storageKey, storedBytes);
    } catch (IOException | RuntimeException exception) {
      deletePartialFile(partialFile, exception);
      throw exception;
    }
  }

  @Override
  public void delete(String storageKey) throws IOException {
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("A chave de armazenamento é obrigatória.");
    }

    Path file = baseDirectory.resolve(storageKey).normalize();
    if (!file.startsWith(baseDirectory) || file.equals(baseDirectory)) {
      throw new IllegalArgumentException("A chave de armazenamento é inválida.");
    }
    Files.deleteIfExists(file);
  }

  private static long copyWithBoundedMemory(InputStream content, Path partialFile) throws IOException {
    byte[] buffer = new byte[COPY_BUFFER_SIZE_IN_BYTES];
    long storedBytes = 0;

    try (OutputStream output = Files.newOutputStream(partialFile, WRITE)) {
      int readBytes;
      while ((readBytes = content.read(buffer)) != -1) {
        output.write(buffer, 0, readBytes);
        storedBytes = Math.addExact(storedBytes, readBytes);
      }
    }

    return storedBytes;
  }

  private static void ensureFinalFileDoesNotExist(Path finalFile) throws FileAlreadyExistsException {
    if (Files.exists(finalFile)) {
      throw new FileAlreadyExistsException(finalFile.toString());
    }
  }

  private static void deletePartialFile(Path partialFile, Exception originalException) {
    try {
      Files.deleteIfExists(partialFile);
    } catch (IOException cleanupException) {
      originalException.addSuppressed(cleanupException);
    }
  }
}
