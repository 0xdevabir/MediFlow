package com.mediflow.pharmacy.dispensing;

import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.repo.PatientRepository;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Safe default: QR tokens are never accepted here (T02 owns signing). Two-identifier checks compare
 * against MRN, national ID, phone (normalised digits), and "given family" display name.
 */
public class DefaultPatientIdentityVerifier implements PatientIdentityPort {

  private final PatientRepository patients;

  public DefaultPatientIdentityVerifier(PatientRepository patients) {
    this.patients = patients;
  }

  @Override
  public Optional<VerifiedIdentity> verifyQr(String qrToken) {
    // Safe default — never guess from an opaque token without T02's verifier.
    return Optional.empty();
  }

  @Override
  public Optional<VerifiedIdentity> verifyTwoIdentifiers(
      UUID patientId, String identifierA, String identifierB) {
    if (patientId == null || blank(identifierA) || blank(identifierB)) {
      return Optional.empty();
    }
    if (norm(identifierA).equals(norm(identifierB))) {
      return Optional.empty(); // two *distinct* identifiers required
    }
    Optional<Patient> opt = patients.findById(patientId);
    if (opt.isEmpty()) {
      return Optional.empty();
    }
    Patient p = opt.get();
    if (matches(p, identifierA) && matches(p, identifierB)) {
      return Optional.of(new VerifiedIdentity(patientId, Method.TWO_IDENTIFIERS));
    }
    return Optional.empty();
  }

  private static boolean matches(Patient p, String raw) {
    String n = norm(raw);
    if (n.isEmpty()) {
      return false;
    }
    if (p.getMrn() != null && norm(p.getMrn()).equals(n)) {
      return true;
    }
    if (p.getNationalId() != null && norm(p.getNationalId()).equals(n)) {
      return true;
    }
    if (p.getPhone() != null
        && digits(p.getPhone()).equals(digits(raw))
        && digits(raw).length() >= 8) {
      return true;
    }
    String full =
        norm(
            Objects.toString(p.getGivenName(), "")
                + " "
                + Objects.toString(p.getFamilyName(), ""));
    return !full.isBlank() && full.equals(n);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String norm(String s) {
    return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static String digits(String s) {
    return s == null ? "" : s.replaceAll("\\D+", "");
  }
}
