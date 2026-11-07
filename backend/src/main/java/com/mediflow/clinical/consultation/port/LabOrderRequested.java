package com.mediflow.clinical.consultation.port;

import java.time.Instant;
import java.util.UUID;

/** Local event until T00 adds it to {@code DomainEvents} (see docs/plan/requests/T05.md). */
public record LabOrderRequested(
    UUID encounterId,
    UUID patientId,
    UUID hospitalId,
    String testCode,
    UUID requestedBy,
    Instant at) {}
