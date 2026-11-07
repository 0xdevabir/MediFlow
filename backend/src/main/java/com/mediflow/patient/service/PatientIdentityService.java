package com.mediflow.patient.service;

import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.repo.PatientProfileRepository;
import com.mediflow.shared.domain.Patient;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-identifier match helper (risk R5): MRN or national ID plus date of birth. Used by pharmacy /
 * lab check-in flows in later modules; audited on every attempt.
 */
@Service
public class PatientIdentityService {

  private final PatientLookupRepository patients;
  private final PatientProfileRepository profiles;
  private final PatientAudit audit;

  public PatientIdentityService(
      PatientLookupRepository patients, PatientProfileRepository profiles, PatientAudit audit) {
    this.patients = patients;
    this.profiles = profiles;
    this.audit = audit;
  }

  public record Match(UUID patientId, String mrn) {}

  @Transactional(readOnly = true)
  public Optional<Match> verify(String mrnOrNationalId, LocalDate dob) {
    if (mrnOrNationalId == null || mrnOrNationalId.isBlank() || dob == null) {
      return Optional.empty();
    }
    String raw = mrnOrNationalId.trim();
    Optional<Patient> found;
    if (MrnGenerator.looksLikeMrn(raw)) {
      found = patients.findByMrn(raw.trim().toUpperCase());
    } else {
      String hash = BlindIndex.nationalIdHash(raw);
      found =
          hash == null
              ? Optional.empty()
              : profiles.findByNationalIdHash(hash).flatMap(p -> patients.findById(p.getPatientId()));
    }
    if (found.isEmpty() || !Objects.equals(found.get().getDateOfBirth(), dob)) {
      audit.record(null, "IDENTITY_VERIFY_FAILED", PatientAudit.PATIENT, null);
      return Optional.empty();
    }
    Patient p = found.get();
    audit.record(p.getId(), "IDENTITY_VERIFIED", PatientAudit.PATIENT, p.getId());
    return Optional.of(new Match(p.getId(), p.getMrn()));
  }
}
