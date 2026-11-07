package com.mediflow.lab.service;

import com.mediflow.lab.domain.LabOrderDetail;
import com.mediflow.lab.repo.LabPatientQueries;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Object-level checks for lab orders. Fine-grained clinical assignment is owned by T01; here we
 * apply role + ownership rules with {@link PatientAccessPort} when a doctor is involved.
 */
@Component
public class LabAccess {

  private final CurrentUser currentUser;
  private final LabPatientQueries patients;
  private final PatientAccessPort patientAccess;

  public LabAccess(
      CurrentUser currentUser, LabPatientQueries patients, PatientAccessPort patientAccess) {
    this.currentUser = currentUser;
    this.patients = patients;
    this.patientAccess = patientAccess;
  }

  public Optional<Patient> currentPatient() {
    return currentUser.userId().flatMap(patients::findByUserId);
  }

  public UUID requireCurrentPatientId() {
    return currentPatient()
        .map(Patient::getId)
        .orElseThrow(() -> new LabRuleException("lab.error.noPatientProfile"));
  }

  public UUID requireUserId() {
    return currentUser.requireUserId();
  }

  public boolean isLabTech() {
    return currentUser.hasRole("LAB_TECH");
  }

  public boolean isFrontDesk() {
    return currentUser.hasRole("FRONT_DESK");
  }

  public boolean isDoctor() {
    return currentUser.hasRole("DOCTOR");
  }

  public boolean isAdmin() {
    return currentUser.hasRole("ADMIN");
  }

  /** Patient: own orders. Lab tech / front desk / admin: staff. Doctor: assigned patients only. */
  public void requireCanViewOrder(LabOrder order, LabOrderDetail detail) {
    if (isLabTech() || isFrontDesk() || isAdmin()) {
      if (isLabTech() && detail != null && detail.getAssignedTechId() != null) {
        UUID me = currentUser.userId().orElse(null);
        if (me != null && !me.equals(detail.getAssignedTechId()) && !isAdmin()) {
          // Unassigned or own assigned orders only after collection — see requireCanHandle.
        }
      }
      return;
    }
    if (isDoctor()) {
      UUID doctorId = currentUser.requireUserId();
      if (!patientAccess.isAssigned(doctorId, order.getPatientId())) {
        throw new AccessDeniedException("Doctor is not assigned to this patient");
      }
      return;
    }
    UUID mine = currentPatient().map(Patient::getId).orElse(null);
    if (mine == null || !mine.equals(order.getPatientId())) {
      throw new AccessDeniedException("Not your lab order");
    }
  }

  /**
   * Lab tech may handle an order if it is still unassigned, or they are the assigned tech. Admin
   * always may.
   */
  public void requireCanHandle(LabOrderDetail detail) {
    if (isAdmin()) {
      return;
    }
    if (!isLabTech()) {
      throw new AccessDeniedException("Lab tech role required");
    }
    UUID me = currentUser.requireUserId();
    if (detail.getAssignedTechId() != null && !Objects.equals(detail.getAssignedTechId(), me)) {
      throw new AccessDeniedException("Order is handled by another lab tech");
    }
  }

  public Patient requirePatientByMrn(String mrn) {
    if (mrn == null || mrn.isBlank()) {
      throw new LabRuleException("lab.error.patientNotFound");
    }
    return patients
        .findByMrn(mrn.trim())
        .orElseThrow(() -> new LabRuleException("lab.error.patientNotFound"));
  }
}
