package com.mediflow.identity.access;

import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.PrescriptionStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only facts the access rules depend on. The default implementation ({@link JdbcAccessFacts})
 * queries the shared schema directly so identity does not depend on other modules' services.
 */
public interface AccessFacts {

  record LabOrderRef(UUID id, UUID patientId, LabOrderStatus status, Instant updatedAt) {}

  record PrescriptionRef(UUID id, UUID patientId, PrescriptionStatus status, Instant signedAt) {}

  Optional<UUID> userIdByUsername(String username);

  /** {@code patient.id} whose {@code user_id} is this app user (patients only). */
  Optional<UUID> patientIdOfUser(UUID userId);

  /** If {@code id} is a {@code practitioner.id}, its linked {@code app_user.id}. */
  Optional<UUID> userIdOfPractitioner(UUID id);

  /** Checked-in appointment / encounter with this doctor today (clinic time zone). */
  boolean seenByDoctorToday(UUID doctorUserId, UUID patientId, Instant now);

  /** Active (not revoked, not expired) referral share granted to this user. */
  boolean activeReferral(UUID userId, UUID patientId, Instant now);

  /** Active (started, not expired) break-glass row for this user and patient. */
  boolean activeBreakGlass(UUID userId, UUID patientId, Instant now);

  /** A Red Alert for the patient that has not been handed over / closed yet. */
  boolean activeRedAlert(UUID patientId);

  /**
   * Active Red Alert for {@code patientId} where {@code erUserId} is the assignee, or the alert's
   * {@code assigned_er_user_id} is null (hospital ER pool for unassigned alerts). R&D §12 wants
   * assignee-scoped ER access; null assignee is the documented fallback until every raise sets one.
   */
  boolean erAssignedOnActiveAlert(UUID erUserId, UUID patientId);

  /** Patient has a lab order the lab is still handling (minimum-necessary link for lab techs). */
  boolean openLabOrderForPatient(UUID patientId, Instant now);

  /** Patient has a recently signed prescription the pharmacy may dispense. */
  boolean signedPrescriptionForPatient(UUID patientId, Instant now);

  boolean userHasRole(UUID userId, com.mediflow.shared.domain.Role role);

  Optional<LabOrderRef> labOrder(UUID labOrderId);

  Optional<PrescriptionRef> prescription(UUID prescriptionId);
}
