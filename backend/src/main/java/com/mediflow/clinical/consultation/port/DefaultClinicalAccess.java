package com.mediflow.clinical.consultation.port;

import com.mediflow.clinical.consultation.repo.ClinicalAppointmentRepository;
import com.mediflow.clinical.consultation.repo.ClinicalEncounterRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPractitionerRepository;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.EncounterStatus;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Safe default when no other {@link ClinicalAccess} bean exists. Nurses/ER/admin may write vitals;
 * doctors consult when assigned via {@link PatientAccessPort} or named on an open encounter /
 * checked-in appointment.
 */
public class DefaultClinicalAccess implements ClinicalAccess {

  private static final Set<AppointmentStatus> ACTIVE_APPT =
      EnumSet.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS);

  private final CurrentUser currentUser;
  private final PatientAccessPort patientAccess;
  private final ClinicalPractitionerRepository practitioners;
  private final ClinicalEncounterRepository encounters;
  private final ClinicalAppointmentRepository appointments;

  public DefaultClinicalAccess(
      CurrentUser currentUser,
      PatientAccessPort patientAccess,
      ClinicalPractitionerRepository practitioners,
      ClinicalEncounterRepository encounters,
      ClinicalAppointmentRepository appointments) {
    this.currentUser = currentUser;
    this.patientAccess = patientAccess;
    this.practitioners = practitioners;
    this.encounters = encounters;
    this.appointments = appointments;
  }

  @Override
  public boolean canReadPatient(UUID patientId) {
    if (staffRead()) {
      return true;
    }
    return canConsult(patientId);
  }

  @Override
  public boolean canWriteVitals(UUID patientId) {
    if (currentUser.hasRole("ADMIN")
        || currentUser.hasRole("NURSE")
        || currentUser.hasRole("ER")) {
      return true;
    }
    return canConsult(patientId);
  }

  @Override
  public boolean canConsult(UUID patientId) {
    if (currentUser.hasRole("ADMIN")) {
      return true;
    }
    if (!currentUser.hasRole("DOCTOR") && !currentUser.hasRole("ER")) {
      return false;
    }
    Optional<UUID> userId = currentUser.userId();
    if (userId.isEmpty()) {
      return false;
    }
    Optional<Practitioner> practitioner = practitioners.findByUserId(userId.get());
    if (practitioner.isPresent()
        && patientAccess.isAssigned(practitioner.get().getId(), patientId)) {
      return true;
    }
    if (patientAccess.isAssigned(userId.get(), patientId)) {
      return true;
    }
    if (practitioner.isEmpty()) {
      return false;
    }
    UUID pid = practitioner.get().getId();
    List<Encounter> open =
        encounters.findByPatientIdAndStatus(patientId, EncounterStatus.IN_PROGRESS);
    for (Encounter e : open) {
      if (pid.equals(e.getDoctorId())) {
        return true;
      }
    }
    List<Appointment> appts = appointments.findByPatientIdAndStatusIn(patientId, ACTIVE_APPT);
    for (Appointment a : appts) {
      if (pid.equals(a.getDoctorId())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canOrderLab(UUID patientId) {
    return canConsult(patientId);
  }

  private boolean staffRead() {
    return currentUser.hasRole("ADMIN")
        || currentUser.hasRole("NURSE")
        || currentUser.hasRole("ER")
        || currentUser.hasRole("FRONT_DESK");
  }
}
