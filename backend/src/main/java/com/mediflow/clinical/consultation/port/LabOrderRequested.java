package com.mediflow.clinical.consultation.port;

import com.mediflow.shared.event.DomainEvents;
import java.time.Instant;
import java.util.UUID;

/**
 * @deprecated Prefer {@link DomainEvents.LabOrderRequested}.
 */
@Deprecated
public record LabOrderRequested(
    UUID encounterId,
    UUID patientId,
    UUID hospitalId,
    String testCode,
    UUID requestedBy,
    Instant at) {

  public DomainEvents.LabOrderRequested toShared() {
    return new DomainEvents.LabOrderRequested(
        encounterId, patientId, hospitalId, testCode, requestedBy, at);
  }
}
