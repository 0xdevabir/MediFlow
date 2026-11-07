package com.mediflow.pharmacy.dispensing;

import java.time.Instant;
import java.util.UUID;

/**
 * Module-local events (not yet in {@code DomainEvents}). T06 should listen for substitution /
 * send-back so doctors can act in the prescription UI; request filed in {@code docs/plan/requests/T08.md}.
 */
public final class PharmacyEvents {

  private PharmacyEvents() {}

  public record SendBackRequested(
      UUID requestId, UUID prescriptionId, UUID patientId, UUID doctorUserId, String reason, Instant at) {}

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

  public record RevokedRxAlreadyDispensedAlert(
      UUID prescriptionId, UUID patientId, Instant revokedAt) {}
}
