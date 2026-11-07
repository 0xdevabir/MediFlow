package com.mediflow.shared.encryption;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Holds the AES-256 key for JPA converters (instantiated outside Spring). */
public final class FieldEncryptionKeyHolder {

  private static volatile SecretKey key = new SecretKeySpec(new byte[32], "AES");

  private FieldEncryptionKeyHolder() {}

  public static SecretKey getKey() {
    return key;
  }

  public static void setKey(byte[] rawKey) {
    if (rawKey == null || rawKey.length != 32) {
      throw new IllegalArgumentException("AES-256 key must be 32 bytes");
    }
    key = new SecretKeySpec(rawKey.clone(), "AES");
  }
}
