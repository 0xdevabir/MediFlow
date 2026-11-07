package com.mediflow.identity.mfa;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.util.Utils;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/** RFC 6238 TOTP (SHA-1, 6 digits, 30 s, ±1 step drift) with replay protection by time step. */
@Component
public class TotpService {

  public static final int PERIOD_SECONDS = 30;
  public static final int DIGITS = 6;
  public static final int ALLOWED_DRIFT_STEPS = 1;
  static final String ISSUER = "MediFlow";

  private final DefaultSecretGenerator secrets = new DefaultSecretGenerator(32);
  private final DefaultCodeGenerator codes = new DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS);

  public String newSecret() {
    return secrets.generate();
  }

  public static long step(Instant at) {
    return Math.floorDiv(at.getEpochSecond(), PERIOD_SECONDS);
  }

  /** Code for a step; used by tests and by {@link #matchingStep}. */
  public String codeAt(String secret, long step) {
    try {
      return codes.generate(secret, step);
    } catch (CodeGenerationException e) {
      throw new IllegalStateException("TOTP generation failed", e);
    }
  }

  /**
   * The time step the code matches within the drift window, provided it is newer than {@code
   * lastUsedStep} (a code can be used once).
   */
  public OptionalLong matchingStep(String secret, String code, Instant now, Long lastUsedStep) {
    if (secret == null || code == null) {
      return OptionalLong.empty();
    }
    String trimmed = code.replace(" ", "").trim();
    if (!trimmed.matches("\\d{" + DIGITS + "}")) {
      return OptionalLong.empty();
    }
    long current = step(now);
    for (long s = current - ALLOWED_DRIFT_STEPS; s <= current + ALLOWED_DRIFT_STEPS; s++) {
      if (lastUsedStep != null && s <= lastUsedStep) {
        continue;
      }
      if (MessageDigest.isEqual(
          codeAt(secret, s).getBytes(StandardCharsets.US_ASCII),
          trimmed.getBytes(StandardCharsets.US_ASCII))) {
        return OptionalLong.of(s);
      }
    }
    return OptionalLong.empty();
  }

  /** {@code otpauth://} URI as a PNG data URI for the enrolment page (CSP allows {@code data:}). */
  public String qrDataUri(String username, String secret) {
    QrData data =
        new QrData.Builder()
            .label(username)
            .secret(secret)
            .issuer(ISSUER)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(DIGITS)
            .period(PERIOD_SECONDS)
            .build();
    ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
    try {
      return Utils.getDataUriForImage(generator.generate(data), generator.getImageMimeType());
    } catch (QrGenerationException e) {
      throw new IllegalStateException("QR generation failed", e);
    }
  }
}
