package com.mediflow.prescription.support;

import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.repo.EncounterRepository;
import com.mediflow.shared.repo.PractitionerRepository;
import com.mediflow.shared.web.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Doctor-only access for prescription drafts. Prefer {@link PatientAccessPort}; also allow the
 * encounter's assigned practitioner (T05 pattern) so drafts work when the port stub denies.
 */
@Component
public class RxAccess {

  private final CurrentUser currentUser;
  private final PatientAccessPort patientAccess;
  private final PractitionerRepository practitioners;
  private final EncounterRepository encounters;

  public RxAccess(
      CurrentUser currentUser,
      PatientAccessPort patientAccess,
      PractitionerRepository practitioners,
      EncounterRepository encounters) {
    this.currentUser = currentUser;
    this.patientAccess = patientAccess;
    this.practitioners = practitioners;
    this.encounters = encounters;
  }

  public UUID requireDoctorUserId() {
    if (!currentUser.hasRole("DOCTOR")) {
      throw new AccessDeniedException("Doctors only");
    }
    return currentUser.requireUserId();
  }

  public void requireCanPrescribe(UUID patientId, UUID encounterId) {
    UUID userId = requireDoctorUserId();
    Optional<Practitioner> me =
        practitioners.findAll().stream().filter(p -> userId.equals(p.getUserId())).findFirst();
    if (me.isPresent() && patientAccess.isAssigned(me.get().getId(), patientId)) {
      return;
    }
    Encounter encounter =
        encounters
            .findById(encounterId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "encounter"));
    if (!encounter.getPatientId().equals(patientId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient/encounter mismatch");
    }
    if (me.isPresent() && me.get().getId().equals(encounter.getDoctorId())) {
      return;
    }
    // Admin break-glass path is owned by T01; deny by default here.
    throw new AccessDeniedException("Not assigned to patient");
  }

  public Optional<Practitioner> practitionerFor(UUID userId) {
    return practitioners.findAll().stream().filter(p -> userId.equals(p.getUserId())).findFirst();
  }
}
