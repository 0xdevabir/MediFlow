package com.mediflow.identity.access;

import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.PatientAccessPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Real {@link PatientAccessPort}: a doctor is assigned to a patient if the patient has a checked-in
 * appointment / encounter with that doctor today, an active referral share, or an active
 * break-glass row; an ER user if they are the Red Alert assignee (or unassigned pool / break-glass).
 *
 * <p>{@code doctorId} may be an {@code app_user.id} (preferred, e.g. {@code
 * CurrentUser.requireUserId()}) or a {@code practitioner.id}; the latter is mapped to its user.
 */
@Component
public class IdentityPatientAccessPort implements PatientAccessPort {

  private final AccessFacts facts;
  private final ClockPort clock;

  public IdentityPatientAccessPort(AccessFacts facts, ClockPort clock) {
    this.facts = facts;
    this.clock = clock;
  }

  @Override
  public boolean isAssigned(UUID doctorId, UUID patientId) {
    if (doctorId == null || patientId == null) {
      return false;
    }
    UUID userId = facts.userIdOfPractitioner(doctorId).orElse(doctorId);
    Instant now = clock.now();
    return facts.seenByDoctorToday(userId, patientId, now)
        || facts.activeReferral(userId, patientId, now)
        || facts.activeBreakGlass(userId, patientId, now)
        || (facts.userHasRole(userId, Role.ROLE_ER)
            && facts.erAssignedOnActiveAlert(userId, patientId));
  }

  /**
   * ER rule (K2): active Red Alert assigned to this ER user, or unassigned alert (null assignee =
   * hospital ER pool), or active break-glass.
   */
  @Override
  public boolean isErAssigned(UUID erUserId, UUID patientId) {
    if (erUserId == null || patientId == null) {
      return false;
    }
    return facts.erAssignedOnActiveAlert(erUserId, patientId)
        || facts.activeBreakGlass(erUserId, patientId, clock.now());
  }
}
