package com.mediflow.audit.chain;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit_event row as it takes part in the hash chain. {@code details} is canonical JSON text
 * (see {@link CanonicalJson}) or {@code null}.
 */
public record AuditRow(
    long seq,
    UUID id,
    Instant occurredAt,
    UUID actorUserId,
    UUID patientId,
    String action,
    String resourceType,
    UUID resourceId,
    String details,
    String prevHash,
    String hash) {}
