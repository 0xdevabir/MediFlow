package com.mediflow.shared.event;

import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.domain.VitalClass;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cross-module domain events. Owners publish; listeners use {@code @TransactionalEventListener}. */
public final class DomainEvents {

  private DomainEvents() {}

  public record PatientRegistered(UUID patientId, UUID userId, Instant at) {}

  public record AppointmentBooked(UUID appointmentId, UUID patientId, UUID hospitalId, Instant at) {}

  public record AppointmentCancelled(UUID appointmentId, UUID patientId, Instant at) {}

  public record AppointmentCheckedIn(UUID appointmentId, UUID patientId, Instant at) {}

  /** Patient is called next in the OPD queue (T04 → T10 QUEUE_NEXT). */
  public record QueueCalled(UUID appointmentId, UUID patientId, String tokenNo, Instant at) {}

  /**
   * Appointment moved to a new start time (T04 reschedule → T10 SCHEDULE_CHANGED + reminder
   * replan).
   */
  public record ScheduleChanged(
      UUID appointmentId, UUID patientId, Instant newStart, Instant at) {}

  /**
   * Doctor schedule / role change that affects already-booked slots (directory → notifications).
   * Slot counters and {@code roleBecameInactive} were promoted from {@code
   * DirectoryScheduleChangeDetail}.
   */
  public record PractitionerScheduleChanged(
      UUID practitionerRoleId,
      UUID practitionerId,
      UUID hospitalId,
      List<UUID> affectedBookedSlotIds,
      int slotsCreated,
      int slotsUpdated,
      int slotsRemoved,
      int slotsClosed,
      boolean roleBecameInactive,
      Instant at) {

    public PractitionerScheduleChanged {
      affectedBookedSlotIds =
          affectedBookedSlotIds == null ? List.of() : List.copyOf(affectedBookedSlotIds);
    }

    /** Compatibility constructor used by older publishers / tests (counters default to 0). */
    public PractitionerScheduleChanged(
        UUID practitionerRoleId,
        UUID practitionerId,
        UUID hospitalId,
        List<UUID> affectedBookedSlotIds,
        Instant at) {
      this(
          practitionerRoleId,
          practitionerId,
          hospitalId,
          affectedBookedSlotIds,
          0,
          0,
          0,
          0,
          false,
          at);
    }
  }

  public record TriageLevelSet(UUID appointmentId, UUID patientId, TriageLevel level, Instant at) {}

  public record VitalsClassified(
      UUID encounterId, UUID patientId, VitalClass patientLevel, Instant at) {}

  public record RedAlertRaised(
      UUID redAlertId, UUID patientId, UUID hospitalId, TriageLevel level, Instant at) {}

  /** Consultation requested a lab order before the lab module creates the row (T05 → T07). */
  public record LabOrderRequested(
      UUID encounterId,
      UUID patientId,
      UUID hospitalId,
      String testCode,
      UUID requestedBy,
      Instant at) {}

  public record LabOrderCreated(
      UUID labOrderId, UUID patientId, UUID hospitalId, String orderType, Instant at) {}

  public record LabReportVerified(
      UUID reportId,
      UUID labOrderId,
      UUID patientId,
      boolean abnormal,
      boolean critical,
      Instant at) {}

  public record PrescriptionSigned(UUID prescriptionId, UUID patientId, UUID signedBy, Instant at) {}

  public record PrescriptionRevoked(UUID prescriptionId, UUID patientId, Instant at) {}

  public record PrescriptionAmended(UUID prescriptionId, UUID patientId, Instant at) {}

  public record MedicationDispensed(
      UUID dispenseId, UUID prescriptionId, UUID patientId, Instant at) {}

  /** Pharmacist sent a signed Rx back to the doctor (pharmacy → Rx UI). */
  public record SendBackRequested(
      UUID requestId, UUID prescriptionId, UUID patientId, UUID doctorUserId, String reason, Instant at) {}

  /** Pharmacist proposed a same-generic brand substitution awaiting doctor decision. */
  public record SubstitutionRequested(
      UUID requestId,
      UUID prescriptionId,
      UUID prescriptionItemId,
      UUID patientId,
      UUID doctorUserId,
      UUID proposedBrandId,
      String reason,
      Instant at) {}

  public record SubstitutionDecided(
      UUID requestId, UUID prescriptionId, boolean approved, UUID decidedBy, Instant at) {}

  public record ReferredElsewhere(
      UUID requestId, UUID prescriptionId, UUID patientId, String reason, Instant at) {}

  /** Revoke arrived after dispense — pharmacy / doctor alert. */
  public record RevokedRxAlreadyDispensedAlert(
      UUID prescriptionId, UUID patientId, Instant revokedAt) {}

  /** Follow-up date set on a consultation / prescription (T05/T06 → T10 reminders). */
  public record FollowUpScheduled(
      UUID sourceId, UUID patientId, LocalDate date, Instant at) {}

  public record AiJobCompleted(UUID jobId, UUID encounterId, String status, Instant at) {}

  public record AiJobFailed(UUID jobId, UUID encounterId, String reason, Instant at) {}
}
