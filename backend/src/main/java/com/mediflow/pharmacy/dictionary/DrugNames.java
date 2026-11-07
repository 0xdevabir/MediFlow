package com.mediflow.pharmacy.dictionary;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Name normalisation and a Java port of pg_trgm similarity (used when pg_trgm is unavailable). */
public final class DrugNames {

  private DrugNames() {}

  /** NFKC, trimmed, lower-case, single spaces. Null/blank → "". */
  public static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    return s.replaceAll("\\s+", " ");
  }

  /** Display form for storing: NFKC, trimmed, single spaces, case preserved. */
  public static String clean(String raw) {
    if (raw == null) {
      return null;
    }
    String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
    return s.isEmpty() ? null : s;
  }

  /**
   * pg_trgm-compatible similarity: words of alphanumerics, each padded with two leading and one
   * trailing space; |A∩B| / |A∪B| over the trigram sets.
   */
  public static double similarity(String a, String b) {
    Set<String> ta = trigrams(a);
    Set<String> tb = trigrams(b);
    if (ta.isEmpty() || tb.isEmpty()) {
      return 0.0;
    }
    Set<String> inter = new HashSet<>(ta);
    inter.retainAll(tb);
    int union = ta.size() + tb.size() - inter.size();
    return union == 0 ? 0.0 : (double) inter.size() / union;
  }

  static Set<String> trigrams(String text) {
    Set<String> out = new HashSet<>();
    for (String word : normalize(text).split("[^\\p{L}\\p{N}]+")) {
      if (word.isEmpty()) {
        continue;
      }
      String padded = "  " + word + " ";
      for (int i = 0; i + 3 <= padded.length(); i++) {
        out.add(padded.substring(i, i + 3));
      }
    }
    return out;
  }
}
