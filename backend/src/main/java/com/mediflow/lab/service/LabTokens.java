package com.mediflow.lab.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/** Random booking references, QR identity tokens and sample barcodes. */
final class LabTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

  private LabTokens() {}

  static String bookingRef() {
    return "LB-" + randomChars(8);
  }

  /** 256-bit URL-safe token printed as a QR code; carries no patient data. */
  static String qrToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String sampleBarcode(LocalDate day) {
    return "S" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + randomChars(6);
  }

  /** Constant-time comparison so the identity check does not leak the token by timing. */
  static boolean tokenMatches(String expected, String presented) {
    if (expected == null || presented == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), presented.trim().getBytes(StandardCharsets.UTF_8));
  }

  private static String randomChars(int n) {
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
    }
    return sb.toString();
  }
}
