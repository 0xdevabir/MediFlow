package com.mediflow.appointment.support;

import java.security.SecureRandom;

/** Short random check-in codes (shown as QR / text on the patient's booking page). */
public final class CheckinCodes {

  private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private CheckinCodes() {}

  public static String next() {
    char[] out = new char[10];
    for (int i = 0; i < out.length; i++) {
      out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
    }
    return new String(out);
  }

  public static String normalise(String raw) {
    return raw == null ? "" : raw.trim().toUpperCase().replace("-", "").replace(" ", "");
  }
}
