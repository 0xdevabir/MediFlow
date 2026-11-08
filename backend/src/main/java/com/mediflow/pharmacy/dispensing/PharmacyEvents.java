package com.mediflow.pharmacy.dispensing;

import com.mediflow.shared.event.DomainEvents;
import java.time.Instant;
import java.util.UUID;

/**
 * @deprecated Prefer {@link DomainEvents} pharmacy lifecycle records (promoted in V2-13). Kept as
 *     type aliases so any remaining module-local listeners compile during transition.
 */
@Deprecated(forRemoval = false)
public final class PharmacyEvents {

  private PharmacyEvents() {}

  @Deprecated
  public record SendBackRequested(
      UUID requestId, UUID prescriptionId, UUID patientId, UUID doctorUserId, String reason, Instant at) {
    public DomainEvents.SendBackRequested toShared() {
      return new DomainEvents.SendBackRequested(
          requestId, prescriptionId, patientId, doctorUserId, reason, at);
    }
  }

  @Deprecated
  public record SubstitutionRequested(
      UUID requestId,
      UUID prescriptionId,
      UUID prescriptionItemId,
      UUID patientId,
      UUID doctorUserId,
      UUID proposedBrandId,
      String reason,
      Instant at) {
    public DomainEvents.SubstitutionRequested toShared() {
      return new DomainEvents.SubstitutionRequested(
          requestId,
          prescriptionId,
          prescriptionItemId,
          patientId,
          doctorUserId,
          proposedBrandId,
          reason,
          at);
    }
  }

  @Deprecated
  public record SubstitutionDecided(
      UUID requestId, UUID prescriptionId, boolean approved, UUID decidedBy, Instant at) {
    public DomainEvents.SubstitutionDecided toShared() {
      return new DomainEvents.SubstitutionDecided(
          requestId, prescriptionId, approved, decidedBy, at);
    }
  }

  @Deprecated
  public record ReferredElsewhere(
      UUID requestId, UUID prescriptionId, UUID patientId, String reason, Instant at) {
    public DomainEvents.ReferredElsewhere toShared() {
      return new DomainEvents.ReferredElsewhere(requestId, prescriptionId, patientId, reason, at);
    }
  }

  @Deprecated
  public record RevokedRxAlreadyDispensedAlert(
      UUID prescriptionId, UUID patientId, Instant revokedAt) {
    public DomainEvents.RevokedRxAlreadyDispensedAlert toShared() {
      return new DomainEvents.RevokedRxAlreadyDispensedAlert(prescriptionId, patientId, revokedAt);
    }
  }
}
