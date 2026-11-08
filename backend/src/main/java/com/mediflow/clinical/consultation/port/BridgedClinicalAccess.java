package com.mediflow.clinical.consultation.port;

import com.mediflow.identity.access.AccessPolicy;
import java.util.UUID;

/**
 * Prefers T01 {@link AccessPolicy} ({@code @access}) matrix checks, then falls back to {@link
 * DefaultClinicalAccess} for encounter-/appointment-named doctors when the matrix is still
 * deny-by-default.
 */
public class BridgedClinicalAccess implements ClinicalAccess {

  private final AccessPolicy access;
  private final ClinicalAccess fallback;

  public BridgedClinicalAccess(AccessPolicy access, ClinicalAccess fallback) {
    this.access = access;
    this.fallback = fallback;
  }

  @Override
  public boolean canReadPatient(UUID patientId) {
    if (access.canReadPatient(patientId)
        || access.can("CONSULTATION_NOTES", "R", patientId)
        || access.can("VITALS", "R", patientId)
        || access.can("HISTORY_ALLERGIES", "R", patientId)) {
      return true;
    }
    return fallback.canReadPatient(patientId);
  }

  @Override
  public boolean canWriteVitals(UUID patientId) {
    if (access.can("VITALS", "C", patientId) || access.can("VITALS", "U", patientId)) {
      return true;
    }
    return fallback.canWriteVitals(patientId);
  }

  @Override
  public boolean canConsult(UUID patientId) {
    if (access.isAssigned(patientId)
        || access.can("CONSULTATION_NOTES", "C", patientId)
        || access.can("CONSULTATION_NOTES", "U", patientId)) {
      return true;
    }
    return fallback.canConsult(patientId);
  }

  @Override
  public boolean canOrderLab(UUID patientId) {
    if (access.can("LAB_ORDERS", "C", patientId)) {
      return true;
    }
    return fallback.canOrderLab(patientId);
  }
}
