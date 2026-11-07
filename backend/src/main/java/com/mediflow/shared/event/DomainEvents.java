package com.mediflow.shared.event;

import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.domain.VitalClass;
import java.time.Instant;
import java.util.UUID;

/** Cross-module domain events. Owners publish; listeners use {@code @TransactionalEventListener}. */
public final class DomainEvents {

  private DomainEvents() {}

  public record PatientRegistered(UUID patientId, UUID userId, Instant at) {}

  public record AppointmentBooked(UUID appointmentId, UUID patientId, UUID hospitalId, Instant at) {}

  public record AppointmentCancelled(UUID appointmentId, UUID patientId, Instant at) {}

  public record AppointmentCheckedIn(UUID appointmentId, UUID patientId, Instant at) {}

  public record TriageLevelSet(UUID appointmentId, UUID patientId, TriageLevel level, Instant at) {}

  public record VitalsClassified(
      UUID encounterId, UUID patientId, VitalClass patientLevel, Instant at) {}

  public record RedAlertRaised(
      UUID redAlertId, UUID patientId, UUID hospitalId, TriageLevel level, Instant at) {}

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

  public record AiJobCompleted(UUID jobId, UUID encounterId, String status, Instant at) {}

  public record AiJobFailed(UUID jobId, UUID encounterId, String reason, Instant at) {}
}
