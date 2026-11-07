package com.mediflow.patient.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Normalisation + keyed hashing used for duplicate detection and identifier lookup over encrypted
 * columns. Pure functions; unit-tested in {@code BlindIndexTest}.
 */
public final class BlindIndex {

  private BlindIndex() {}

  /** Upper-case alphanumerics only ("1990 1234-567" and "19901234567" match). */
  public static String normaliseNationalId(String nationalId) {
    if (nationalId == null) {
      return null;
    }
    String n = Normalizer.normalize(nationalId, Normalizer.Form.NFKC)
        .replaceAll("[^\\p{Alnum}]", "")
        .toUpperCase(Locale.ROOT);
    return n.isEmpty() ? null : n;
  }

  /**
   * Digits only; Bangladeshi numbers are reduced to their national form, so "+880 1712-345678",
   * "8801712345678" and "01712345678" match.
   */
  public static String normalisePhone(String phone) {
    if (phone == null) {
      return null;
    }
    String digits = Normalizer.normalize(phone, Normalizer.Form.NFKC).replaceAll("\\D", "");
    if (digits.startsWith("00")) {
      digits = digits.substring(2);
    }
    if (digits.startsWith("880") && digits.length() == 13) {
      digits = "0" + digits.substring(3);
    }
    return digits.isEmpty() ? null : digits;
  }

  /** Lower-case, accents/whitespace folded. */
  public static String normaliseName(String given, String family) {
    String full = ((given == null ? "" : given) + " " + (family == null ? "" : family));
    return Normalizer.normalize(full, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[\\p{Punct}]", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  public static String nationalIdHash(String nationalId) {
    String n = normaliseNationalId(nationalId);
    return n == null ? null : PatientKeys.hmacHex(PatientKeys.BLIND_INDEX, "nid:" + n);
  }

  /** Null when the phone or DOB is missing (no reliable composite key then). */
  public static String identityHash(String given, String family, LocalDate dob, String phone) {
    String p = normalisePhone(phone);
    if (p == null || dob == null) {
      return null;
    }
    return PatientKeys.hmacHex(
        PatientKeys.BLIND_INDEX, "id:" + normaliseName(given, family) + "|" + dob + "|" + p);
  }
}
