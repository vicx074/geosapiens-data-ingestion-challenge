package io.github.vicx074.geosapiens.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import io.github.vicx074.geosapiens.ingestion.domain.IngestionStatus;
import io.github.vicx074.geosapiens.ingestion.infrastructure.storage.FileSystemTemporaryFileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReceiveIngestionUploadTest {

  private static final Instant RECEIVED_AT = Instant.parse("2026-08-25T12:00:00Z");

  @TempDir
  Path temporaryDirectory;

  @Test
  void shouldStoreFileBeforeRegisteringQueuedJob() throws IOException {
    FileSystemTemporaryFileStorage storage = new FileSystemTemporaryFileStorage(
        temporaryDirectory
    );
    RegisterIngestionJob registerJob = mock(RegisterIngestionJob.class);
    when(registerJob.execute(any(), eq("transactions.csv"), eq(RECEIVED_AT)))
        .thenAnswer(invocation -> {
          IngestionJob job = IngestionJob.receive(invocation.getArgument(0), "transactions.csv", RECEIVED_AT);
          job.markQueued(RECEIVED_AT);
          return job;
        });
    ReceiveIngestionUpload receiveUpload = new ReceiveIngestionUpload(
        storage,
        registerJob,
        Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)
    );

    IngestionJob job = receiveUpload.execute(
        "transactions.csv",
        new ByteArrayInputStream("id,amount\n1,10.00".getBytes())
    );

    assertThat(job.getStatus()).isEqualTo(IngestionStatus.QUEUED);
    assertThat(temporaryDirectory.resolve(job.getId() + ".csv"))
        .hasContent("id,amount\n1,10.00");
  }

  @Test
  void shouldDeleteStoredFileWhenJobRegistrationFails() {
    FileSystemTemporaryFileStorage storage = new FileSystemTemporaryFileStorage(
        temporaryDirectory
    );
    RegisterIngestionJob registerJob = mock(RegisterIngestionJob.class);
    when(registerJob.execute(any(), any(), any()))
        .thenThrow(new IllegalStateException("Falha simulada no banco."));
    ReceiveIngestionUpload receiveUpload = new ReceiveIngestionUpload(
        storage,
        registerJob,
        Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)
    );

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> receiveUpload.execute(
            "transactions.csv",
            new ByteArrayInputStream("id,amount\n1,10.00".getBytes())
        ));
    assertThat(temporaryDirectory).isEmptyDirectory();
  }

  @Test
  void shouldRejectAndDeleteEmptyCsv() {
    ReceiveIngestionUpload receiveUpload = new ReceiveIngestionUpload(
        new FileSystemTemporaryFileStorage(temporaryDirectory),
        mock(RegisterIngestionJob.class),
        Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)
    );

    assertThatExceptionOfType(InvalidIngestionUploadException.class)
        .isThrownBy(() -> receiveUpload.execute(
            "transactions.csv",
            new ByteArrayInputStream(new byte[0])
        ))
        .withMessage("O arquivo CSV não pode estar vazio.");
    assertThat(temporaryDirectory).isEmptyDirectory();
  }

  @Test
  void shouldRejectFilenameWithoutCsvExtensionBeforeWriting() throws IOException {
    ReceiveIngestionUpload receiveUpload = new ReceiveIngestionUpload(
        new FileSystemTemporaryFileStorage(temporaryDirectory),
        mock(RegisterIngestionJob.class),
        Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)
    );

    assertThatExceptionOfType(InvalidIngestionUploadException.class)
        .isThrownBy(() -> receiveUpload.execute(
            "transactions.txt",
            new ByteArrayInputStream("data".getBytes())
        ))
        .withMessage("O arquivo enviado deve possuir extensão .csv.");
    assertThat(temporaryDirectory).isEmptyDirectory();
  }
}
