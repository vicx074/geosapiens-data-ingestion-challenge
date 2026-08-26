package com.github.vicx074.geosapiens.ingestion.infrastructure.web;

import com.github.vicx074.geosapiens.ingestion.application.ReceiveIngestionUpload;
import com.github.vicx074.geosapiens.ingestion.domain.IngestionJob;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/imports")
public class IngestionUploadController {

  private final ReceiveIngestionUpload receiveUpload;

  public IngestionUploadController(ReceiveIngestionUpload receiveUpload) {
    this.receiveUpload = receiveUpload;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<AcceptedIngestionResponse> upload(
      @RequestPart("file") MultipartFile file) throws IOException {
    IngestionJob job;
    try (InputStream content = file.getInputStream()) {
      job = receiveUpload.execute(file.getOriginalFilename(), content);
    }

    URI statusUri = URI.create("/imports/" + job.getId());
    return ResponseEntity.accepted()
        .location(statusUri)
        .body(new AcceptedIngestionResponse(job.getId(), job.getStatus().name(), statusUri));
  }

  public record AcceptedIngestionResponse(UUID jobId, String status, URI statusUrl) {
  }
}
