package io.github.vicx074.geosapiens.ingestion.application.port.out;

public interface CsvRowConsumer {

  CsvRowConsumer DISCARDING = new CsvRowConsumer() {
    @Override
    public void accepted(CsvTransactionRow row) {
    }

    @Override
    public void rejected(CsvRowError error) {
    }
  };

  void accepted(CsvTransactionRow row);

  void rejected(CsvRowError error);
}
