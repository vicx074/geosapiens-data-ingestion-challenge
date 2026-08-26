package io.github.vicx074.geosapiens.ingestion.application.port.out;

import java.io.IOException;
import java.io.InputStream;

public interface IngestionCsvProcessor {

  CsvProcessingSummary process(InputStream content, CsvRowConsumer consumer) throws IOException;
}
