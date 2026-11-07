package com.mediflow.audit.chain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic JSON text for hashing: object keys sorted, no whitespace, numbers as plain
 * decimals without trailing zeros. PostgreSQL {@code jsonb} reorders keys and reformats text, so
 * the verifier re-canonicalises what it reads back and gets the same string the writer hashed.
 */
public final class CanonicalJson {

  private final ObjectMapper mapper;

  public CanonicalJson(ObjectMapper mapper) {
    this.mapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  }

  /** Canonical text of a details map; {@code null} for null/empty maps. */
  public String ofMap(Map<String, Object> details) {
    if (details == null || details.isEmpty()) {
      return null;
    }
    return of(mapper.valueToTree(details));
  }

  /** Canonical text of JSON read back from the database; {@code null} stays {@code null}. */
  public String ofText(String json) {
    if (json == null) {
      return null;
    }
    try {
      return of(mapper.readTree(json));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON in audit details", e);
    }
  }

  String of(JsonNode node) {
    StringBuilder out = new StringBuilder();
    write(node, out);
    return out.toString();
  }

  private void write(JsonNode node, StringBuilder out) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      out.append("null");
    } else if (node.isObject()) {
      List<String> names = new ArrayList<>();
      Iterator<String> it = node.fieldNames();
      it.forEachRemaining(names::add);
      names.sort(String::compareTo);
      out.append('{');
      for (int i = 0; i < names.size(); i++) {
        if (i > 0) {
          out.append(',');
        }
        quote(names.get(i), out);
        out.append(':');
        write(node.get(names.get(i)), out);
      }
      out.append('}');
    } else if (node.isArray()) {
      out.append('[');
      for (int i = 0; i < node.size(); i++) {
        if (i > 0) {
          out.append(',');
        }
        write(node.get(i), out);
      }
      out.append(']');
    } else if (node.isNumber()) {
      BigDecimal n = node.decimalValue().stripTrailingZeros();
      out.append(n.signum() == 0 ? "0" : n.toPlainString());
    } else if (node.isBoolean()) {
      out.append(node.booleanValue());
    } else {
      quote(node.asText(), out);
    }
  }

  private static void quote(String s, StringBuilder out) {
    out.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }
}
