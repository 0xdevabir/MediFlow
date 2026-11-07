package com.mediflow.patient.service;

import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Object-level checks for patient data, used as {@code @patientAccess} in {@code @PreAuthorize}.
 * Follows R&D v1.3 §12 / DOCX Table 17. Deny by default:
 *
 * <ul>
 *   <li>Patient: own record only.
 *   <li>Front desk / nurse: demographics (staff scope); front desk also writes them.
 *   <li>Doctor / ER / lab tech / pharmacist: only patients {@link PatientAccessPort#isAssigned}
 *       says they are assigned to (T01 implements the assignment / break-glass rules).
 *   <li>Admin: no patient data.
 * </ul>
 */
@Component("patientAccess")
public class PatientAccessGuard {

  private final CurrentUser currentUser;
  private final PatientLookupRepository patients;
  private final PatientAccessPort accessPort;

  public PatientAccessGuard(
      CurrentUser currentUser, PatientLookupRepository patients, PatientAccessPort accessPort) {
    this.currentUser = currentUser;
    this.patients = patients;
    this.accessPort = accessPort;
  }

  /** The signed-in user's own patient id, if they are a patient with a record. */
  public Optional<UUID> ownPatientId() {
    if (!currentUser.hasRole("PATIENT")) {
      return Optional.empty();
    }
    return currentUser.userId().flatMap(patients::findByUserId).map(p -> p.getId());
  }

  public boolean isSelf(UUID patientId) {
    return patientId != null && ownPatientId().filter(patientId::equals).isPresent();
  }

  /** Demographics read (name, DOB, contact, MRN). */
  public boolean canReadDemographics(UUID patientId) {
    if (patientId == null) {
      return false;
    }
    if (isSelf(patientId)) {
      return true;
    }
    if (currentUser.hasRole("FRONT_DESK") || currentUser.hasRole("NURSE")) {
      return true;
    }
    return clinicalRoleAssigned(patientId, "DOCTOR", "ER", "LAB_TECH", "PHARMACIST");
  }

  /** Demographics create/update by staff. Patients use the contact-only path. */
  public boolean canManageDemographics(UUID patientId) {
    return patientId != null && currentUser.hasRole("FRONT_DESK");
  }

  /** Contact details: the patient themself or front desk. */
  public boolean canUpdateContact(UUID patientId) {
    return isSelf(patientId) || canManageDemographics(patientId);
  }

  /** History & allergies read: patient (own), assigned doctor / ER, assigned pharmacist (†). */
  public boolean canReadClinicalHistory(UUID patientId) {
    if (patientId == null) {
      return false;
    }
    return isSelf(patientId) || clinicalRoleAssigned(patientId, "DOCTOR", "ER", "PHARMACIST");
  }

  /** Doctor-level write of allergies / history (create verified entries, confirm). */
  public boolean canConfirmClinicalHistory(UUID patientId) {
    return patientId != null && clinicalRoleAssigned(patientId, "DOCTOR", "ER");
  }

  /** ID documents: owner or front desk. */
  public boolean canAccessDocuments(UUID patientId) {
    return isSelf(patientId) || canManageDemographics(patientId);
  }

  private boolean clinicalRoleAssigned(UUID patientId, String... roles) {
    for (String role : roles) {
      if (currentUser.hasRole(role)) {
        Optional<UUID> uid = currentUser.userId();
        return uid.isPresent() && accessPort.isAssigned(uid.get(), patientId);
      }
    }
    return false;
  }
}
