package com.mediflow.patient.service;

import com.mediflow.shared.encryption.FieldEncryptionKeyHolder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 helpers with purpose-separated sub-keys derived from the field encryption key
 * ({@code MEDIFLOW_FIELD_ENCRYPTION_KEY}), so no extra secret has to be configured. Each purpose
 * gets its own key (HMAC(master, purpose)), which keeps blind indexes, OTP hashes and QR signatures
 * independent.
 */
public final class PatientKeys {

  public static final String BLIND_INDEX = "mediflow.patient.blind-index.v1";
  public static final String OTP = "mediflow.patient.otp.v1";
  public static final String QR_TOKEN = "mediflow.patient.qr-token.v1";

  private PatientKeys() {}

  public static byte[] hmac(String purpose, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(subKey(purpose), "HmacSHA256"));
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC unavailable", e);
    }
  }

  public static String hmacHex(String purpose, String data) {
    return HexFormat.of().formatHex(hmac(purpose, data.getBytes(StandardCharsets.UTF_8)));
  }

  /** Constant-time comparison. */
  public static boolean equal(byte[] a, byte[] b) {
    return MessageDigest.isEqual(a, b);
  }

  private static byte[] subKey(String purpose) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(FieldEncryptionKeyHolder.getKey().getEncoded(), "HmacSHA256"));
    return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
  }
}
