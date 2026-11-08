package com.mediflow.shared.port;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived QR identity tokens for pharmacy dispense and lab sample hand-off.
 *
 * <p><b>Implementor:</b> V2-03 ({@code com.mediflow.patient}). <b>Consumers:</b> V2-09 pharmacy,
 * V2-08 lab. TTL defaults from {@code mediflow.patient.qr-token-ttl}.
 */
public interface PatientQrPort {

  /** Issues an opaque token bound to {@code patientId} that expires after {@code ttl}. */
  String issueToken(UUID patientId, Duration ttl);

  /** Resolves a still-valid token to the patient id; empty when unknown or expired. */
  Optional<UUID> resolvePatientId(String token);
}
