package io.github.vicx074.geosapiens.ingestion.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class CsvRecordLengthLimitingReaderTest {

  @Test
  void shouldTreatCrLfAsOneRecordSeparatorWithoutChargingItToTheRecord() {
    assertThatCode(() -> consume("a,b\r\nc,d\r\n", 3))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotResetLimitOnNewlineInsideQuotedField() {
    assertThatExceptionOfType(CsvRecordTooLargeException.class)
        .isThrownBy(() -> consume("a,b\n\"abc\ndef\",z\n", 7))
        .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(exception.recordNumber())
            .isEqualTo(2));
  }

  @Test
  void shouldKeepEscapedQuoteInsideQuotedField() {
    assertThatCode(() -> consume("a,b\n\"a\"\"b\",z\n", 8))
        .doesNotThrowAnyException();
  }

  private static void consume(String csv, int maxRecordCharacters) throws IOException {
    try (var reader = new CsvRecordLengthLimitingReader(
        new StringReader(csv),
        maxRecordCharacters)) {
      char[] buffer = new char[3];
      while (reader.read(buffer) != -1) {
        // O buffer pequeno força várias chamadas de read e valida que o estado atravessa chunks.
      }
    }
  }
}
