package com.mediflow.appointment.support;

import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.web.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Object-level checks for appointments (patients see only their own). */
@Component
public class AppointmentAccess {

  private final CurrentUser currentUser;
  private final ApptPatientRepository patients;
  private final ApptPractitionerRepository practitioners;

  public AppointmentAccess(
      CurrentUser currentUser,
      ApptPatientRepository patients,
      ApptPractitionerRepository practitioners) {
    this.currentUser = currentUser;
    this.patients = patients;
    this.practitioners = practitioners;
  }

  public Optional<Patient> currentPatient() {
    return currentUser.userId().flatMap(patients::findByUserId);
  }

  public UUID requireCurrentPatientId() {
    return currentPatient()
        .map(Patient::getId)
        .orElseThrow(() -> new AppointmentException("appointment.error.noPatientProfile"));
  }

  public Optional<Practitioner> currentPractitioner() {
    return currentUser.userId().flatMap(practitioners::findFirstByUserId);
  }

  public UUID requireCurrentDoctorId() {
    return currentPractitioner()
        .map(Practitioner::getId)
        .orElseThrow(() -> new AppointmentException("appointment.error.noPractitioner"));
  }

  public boolean isStaff() {
    return currentUser.hasRole("FRONT_DESK")
        || currentUser.hasRole("NURSE")
        || currentUser.hasRole("DOCTOR")
        || currentUser.hasRole("ER")
        || currentUser.hasRole("ADMIN");
  }

  /** Patients: own appointments only. Staff: any (fine-grained clinical access is T01's). */
  public void requireCanView(Appointment appointment) {
    if (isStaff()) {
      return;
    }
    UUID mine = currentPatient().map(Patient::getId).orElse(null);
    if (mine == null || !mine.equals(appointment.getPatientId())) {
      throw new AccessDeniedException("Not your appointment");
    }
  }

  /** Cancel/reschedule: the patient themself or front desk / admin. */
  public void requireCanChange(Appointment appointment) {
    if (currentUser.hasRole("FRONT_DESK") || currentUser.hasRole("ADMIN")) {
      return;
    }
    UUID mine = currentPatient().map(Patient::getId).orElse(null);
    if (mine == null || !mine.equals(appointment.getPatientId())) {
      throw new AccessDeniedException("Not your appointment");
    }
  }
}
