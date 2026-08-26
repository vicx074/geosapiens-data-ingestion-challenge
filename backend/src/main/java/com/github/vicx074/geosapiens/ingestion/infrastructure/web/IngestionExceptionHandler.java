package com.github.vicx074.geosapiens.ingestion.infrastructure.web;

import com.github.vicx074.geosapiens.ingestion.application.InvalidIngestionUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IngestionExceptionHandler {

  @ExceptionHandler(InvalidIngestionUploadException.class)
  ProblemDetail handleInvalidUpload(InvalidIngestionUploadException exception) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        exception.getMessage()
    );
    problem.setTitle("Upload inválido");
    return problem;
  }
}
