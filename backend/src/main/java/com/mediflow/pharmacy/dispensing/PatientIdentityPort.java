package com.mediflow.pharmacy.dispensing;

import java.util.Optional;
import java.util.UUID;

/**
 * Patient identity check before dispensing. Prefer {@link
 * com.mediflow.shared.port.PatientQrPort} when available; two-identifier fallback remains local.
 */
public interface PatientIdentityPort {

  enum Method {
    QR,
    TWO_IDENTIFIERS
  }

  record VerifiedIdentity(UUID patientId, Method method) {}

  /**
   * Verify a patient QR token via {@code PatientQrPort} when that bean exists; otherwise empty
   * (never guesses).
   */
  Optional<VerifiedIdentity> verifyQr(String qrToken);

  /**
   * Two independent identifiers must both match the patient record (e.g. MRN + national ID, or MRN
   * + phone). Empty/blank values never match.
   */
  Optional<VerifiedIdentity> verifyTwoIdentifiers(
      UUID patientId, String identifierA, String identifierB);
}
