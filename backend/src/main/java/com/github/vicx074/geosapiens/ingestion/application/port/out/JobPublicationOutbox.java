package com.github.vicx074.geosapiens.ingestion.application.port.out;

public interface JobPublicationOutbox {

  void insert(PendingJobPublication publication);
}
