package com.github.vicx074.geosapiens.ingestion.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.github.vicx074.geosapiens.ingestion.application.port.out.StoredTemporaryFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemTemporaryFileStorageTest {

  private static final UUID JOB_ID = UUID.fromString("7dbb46a4-ed58-40da-a6cc-0b70c87f9768");
  private static final long LARGE_FILE_SIZE_IN_BYTES = 20L * 1024 * 1024;

  @TempDir
  Path temporaryDirectory;

  @Test
  void shouldStoreLargeInputWithoutReadingItEntirelyIntoMemory() throws IOException {
    TrackingGeneratedInputStream content = new TrackingGeneratedInputStream(
        LARGE_FILE_SIZE_IN_BYTES
    );
    FileSystemTemporaryFileStorage storage = new FileSystemTemporaryFileStorage(
        temporaryDirectory
    );

    StoredTemporaryFile storedFile = storage.store(JOB_ID, content);

    Path finalFile = temporaryDirectory.resolve(storedFile.storageKey());
    assertThat(storedFile.storageKey()).isEqualTo(JOB_ID + ".csv");
    assertThat(storedFile.sizeInBytes()).isEqualTo(LARGE_FILE_SIZE_IN_BYTES);
    assertThat(Files.size(finalFile)).isEqualTo(LARGE_FILE_SIZE_IN_BYTES);
    assertThat(content.getLargestReadRequest())
        .isEqualTo(FileSystemTemporaryFileStorage.COPY_BUFFER_SIZE_IN_BYTES);
    assertThat(content.wasReadAllBytesCalled()).isFalse();
  }

  @Test
  void shouldPublishOnlyFinalFileAfterSuccessfulCopy() throws IOException {
    FileSystemTemporaryFileStorage storage = new FileSystemTemporaryFileStorage(
        temporaryDirectory
    );

    storage.store(JOB_ID, new TrackingGeneratedInputStream(128));

    try (Stream<Path> files = Files.list(temporaryDirectory)) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .containsExactly(JOB_ID + ".csv");
    }
  }

  @Test
  void shouldDeletePartialFileWhenReadingFails() throws IOException {
    FileSystemTemporaryFileStorage storage = new FileSystemTemporaryFileStorage(
        temporaryDirectory
    );

    assertThatIOException()
        .isThrownBy(() -> storage.store(JOB_ID, new FailingInputStream(128)))
        .withMessage("Falha simulada durante a leitura.");

    try (Stream<Path> files = Files.list(temporaryDirectory)) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void shouldNotOverwriteFileAlreadyStoredForJob() throws IOException {
    FileSystemTemporaryFileStorage storage = new FileSystemTemporaryFileStorage(
        temporaryDirectory
    );
    storage.store(JOB_ID, new TrackingGeneratedInputStream(128));

    assertThatExceptionOfType(FileAlreadyExistsException.class)
        .isThrownBy(() -> storage.store(JOB_ID, new TrackingGeneratedInputStream(256)));
    assertThat(Files.size(temporaryDirectory.resolve(JOB_ID + ".csv"))).isEqualTo(128);
  }

  @Test
  void shouldDeleteStoredFileByItsInternalKey() throws IOException {
    FileSystemTemporaryFileStorage storage = new FileSystemTemporaryFileStorage(
        temporaryDirectory
    );
    StoredTemporaryFile storedFile = storage.store(
        JOB_ID,
        new TrackingGeneratedInputStream(128)
    );

    storage.delete(storedFile.storageKey());

    assertThat(temporaryDirectory.resolve(storedFile.storageKey())).doesNotExist();
  }

  private static final class TrackingGeneratedInputStream extends InputStream {

    private long remainingBytes;
    private int largestReadRequest;
    private boolean readAllBytesCalled;

    private TrackingGeneratedInputStream(long sizeInBytes) {
      this.remainingBytes = sizeInBytes;
    }

    @Override
    public int read() {
      if (remainingBytes == 0) {
        return -1;
      }
      remainingBytes--;
      return 'x';
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      largestReadRequest = Math.max(largestReadRequest, length);
      if (remainingBytes == 0) {
        return -1;
      }

      int readBytes = (int) Math.min(length, remainingBytes);
      remainingBytes -= readBytes;
      return readBytes;
    }

    @Override
    public byte[] readAllBytes() {
      readAllBytesCalled = true;
      throw new AssertionError("A implementação não pode materializar o arquivo completo.");
    }

    private int getLargestReadRequest() {
      return largestReadRequest;
    }

    private boolean wasReadAllBytesCalled() {
      return readAllBytesCalled;
    }
  }

  private static final class FailingInputStream extends InputStream {

    private int remainingBytesBeforeFailure;

    private FailingInputStream(int remainingBytesBeforeFailure) {
      this.remainingBytesBeforeFailure = remainingBytesBeforeFailure;
    }

    @Override
    public int read() throws IOException {
      if (remainingBytesBeforeFailure == 0) {
        throw new IOException("Falha simulada durante a leitura.");
      }
      remainingBytesBeforeFailure--;
      return 'x';
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      if (remainingBytesBeforeFailure == 0) {
        throw new IOException("Falha simulada durante a leitura.");
      }

      int readBytes = Math.min(length, remainingBytesBeforeFailure);
      remainingBytesBeforeFailure -= readBytes;
      return readBytes;
    }
  }
}
