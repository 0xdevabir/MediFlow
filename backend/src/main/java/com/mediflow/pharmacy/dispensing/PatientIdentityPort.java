package com.mediflow.pharmacy.dispensing;

import java.util.Optional;
import java.util.UUID;

/**
 * Patient identity check before dispensing. QR verification belongs to T02; until a shared port
 * exists, the default implementation only supports two-identifier checks (safe default: QR never
 * auto-accepts).
 */
public interface PatientIdentityPort {

  enum Method {
    QR,
    TWO_IDENTIFIERS
  }

  record VerifiedIdentity(UUID patientId, Method method) {}

  /**
   * Verify a patient QR token. Safe default returns empty (never guesses). A T02 bean may replace
   * this when promoted to shared.
   */
  Optional<VerifiedIdentity> verifyQr(String qrToken);

  /**
   * Two independent identifiers must both match the patient record (e.g. MRN + national ID, or MRN
   * + phone). Empty/blank values never match.
   */
  Optional<VerifiedIdentity> verifyTwoIdentifiers(
      UUID patientId, String identifierA, String identifierB);
}
