package com.mediflow.pharmacy.dictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvReaderTest {

  @Test
  void readsQuotedCommas() throws Exception {
    List<CsvReader.Row> rows =
        CsvReader.read(new StringReader("a,b\n\"x,y\",z\n"), 10);
    assertEquals(2, rows.size());
    assertEquals(List.of("x,y", "z"), rows.get(1).fields());
  }

  @Test
  void rejectsTooManyRows() {
    assertThrows(
        IOException.class,
        () -> CsvReader.read(new StringReader("a\n1\n2\n3\n"), 2));
  }
}
