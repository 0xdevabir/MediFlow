package com.mediflow.patient.service;

import com.mediflow.patient.domain.PatientProfile;
import com.mediflow.patient.domain.RegistrationSource;
import com.mediflow.shared.domain.Patient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read model for patient pages. The national ID is masked (last 4 characters only). */
public record PatientView(
    UUID id,
    UUID userId,
    String mrn,
    String givenName,
    String familyName,
    LocalDate dateOfBirth,
    String sex,
    String phone,
    String email,
    String nationalIdMasked,
    String address,
    boolean phoneVerified,
    boolean emailVerified,
    RegistrationSource registrationSource,
    Instant createdAt) {

  public String fullName() {
    return (givenName + " " + familyName).trim();
  }

  static PatientView of(Patient p, PatientProfile profile) {
    return new PatientView(
        p.getId(),
        p.getUserId(),
        p.getMrn(),
        p.getGivenName(),
        p.getFamilyName(),
        p.getDateOfBirth(),
        p.getSex(),
        p.getPhone(),
        p.getEmail(),
        mask(p.getNationalId()),
        p.getAddress(),
        profile != null && profile.isPhoneVerified(),
        profile != null && profile.isEmailVerified(),
        profile == null ? null : profile.getRegistrationSource(),
        p.getCreatedAt());
  }

  static String mask(String nationalId) {
    if (nationalId == null || nationalId.isBlank()) {
      return null;
    }
    String n = nationalId.trim();
    int keep = Math.min(4, n.length());
    return "•".repeat(Math.max(0, n.length() - keep)) + n.substring(n.length() - keep);
  }
}
