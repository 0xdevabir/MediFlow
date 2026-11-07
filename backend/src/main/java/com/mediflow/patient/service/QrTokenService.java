package com.mediflow.patient.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mediflow.shared.port.ClockPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signed, short-lived identity token for the patient card QR (risk R5). Format: {@code
 * base64url("v1|patientId|mrn|expEpochSeconds") + "." + base64url(HMAC-SHA256)}. It carries no
 * name / DOB / national ID; the scanning staff member still sees the record only through the
 * normal access checks.
 */
@Component
public class QrTokenService {

  private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DEC = Base64.getUrlDecoder();

  private final ClockPort clock;
  private final Duration ttl;

  public QrTokenService(
      ClockPort clock, @Value("${mediflow.patient.qr-token-ttl:PT30M}") Duration ttl) {
    this.clock = clock;
    this.ttl = ttl;
  }

  public record QrIdentity(UUID patientId, String mrn, Instant expiresAt) {}

  public record IssuedToken(String token, Instant expiresAt) {}

  public IssuedToken issue(UUID patientId, String mrn) {
    Instant exp = clock.now().plus(ttl);
    String payload = "v1|" + patientId + "|" + mrn + "|" + exp.getEpochSecond();
    byte[] p = payload.getBytes(StandardCharsets.UTF_8);
    String token = ENC.encodeToString(p) + "." + ENC.encodeToString(PatientKeys.hmac(PatientKeys.QR_TOKEN, p));
    return new IssuedToken(token, exp);
  }

  /** Empty when malformed, tampered or expired. */
  public Optional<QrIdentity> verify(String token) {
    if (token == null || token.length() > 512) {
      return Optional.empty();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot != token.lastIndexOf('.')) {
      return Optional.empty();
    }
    try {
      byte[] payload = DEC.decode(token.substring(0, dot));
      byte[] sig = DEC.decode(token.substring(dot + 1));
      if (!PatientKeys.equal(sig, PatientKeys.hmac(PatientKeys.QR_TOKEN, payload))) {
        return Optional.empty();
      }
      String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|");
      if (parts.length != 4 || !"v1".equals(parts[0])) {
        return Optional.empty();
      }
      Instant exp = Instant.ofEpochSecond(Long.parseLong(parts[3]));
      if (!clock.now().isBefore(exp)) {
        return Optional.empty();
      }
      return Optional.of(new QrIdentity(UUID.fromString(parts[1]), parts[2], exp));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** PNG data URI (CSP allows {@code img-src data:}). */
  public String qrDataUri(String content) {
    try {
      BitMatrix matrix =
          new QRCodeWriter()
              .encode(
                  content,
                  BarcodeFormat.QR_CODE,
                  240,
                  240,
                  Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(matrix, "PNG", out);
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (WriterException | IOException e) {
      throw new IllegalStateException("QR generation failed", e);
    }
  }
}
