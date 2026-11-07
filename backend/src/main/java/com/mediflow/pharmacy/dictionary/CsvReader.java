package com.mediflow.pharmacy.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 reader (quoted fields, doubled quotes, embedded commas/newlines, UTF-8 BOM).
 * Returns each record with the 1-based physical line it started on.
 */
public final class CsvReader {

  public record Row(int line, List<String> fields) {

    public boolean blank() {
      return fields.stream().allMatch(f -> f == null || f.isBlank());
    }
  }

  private CsvReader() {}

  public static List<Row> read(Reader in, int maxRows) throws IOException {
    List<Row> rows = new ArrayList<>();
    BufferedReader r = new BufferedReader(in);
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    boolean first = true;
    int line = 1;
    int recordStart = 1;
    int c;
    while ((c = r.read()) != -1) {
      char ch = (char) c;
      if (first) {
        first = false;
        if (ch == '﻿') {
          continue;
        }
      }
      if (inQuotes) {
        if (ch == '"') {
          r.mark(1);
          int next = r.read();
          if (next == '"') {
            field.append('"');
          } else {
            inQuotes = false;
            if (next != -1) {
              r.reset();
            }
          }
        } else {
          if (ch == '\n') {
            line++;
          }
          field.append(ch);
        }
        continue;
      }
      switch (ch) {
        case '"' -> inQuotes = true;
        case ',' -> {
          fields.add(field.toString());
          field.setLength(0);
        }
        case '\r' -> {
          // swallowed; '\n' ends the record
        }
        case '\n' -> {
          fields.add(field.toString());
          field.setLength(0);
          rows.add(new Row(recordStart, List.copyOf(fields)));
          fields.clear();
          line++;
          recordStart = line;
          if (rows.size() > maxRows) {
            throw new IOException("Too many rows (max " + maxRows + ")");
          }
        }
        default -> field.append(ch);
      }
    }
    if (field.length() > 0 || !fields.isEmpty()) {
      fields.add(field.toString());
      rows.add(new Row(recordStart, List.copyOf(fields)));
    }
    if (inQuotes) {
      throw new IOException("Unterminated quoted field starting on line " + recordStart);
    }
    return rows;
  }
}
