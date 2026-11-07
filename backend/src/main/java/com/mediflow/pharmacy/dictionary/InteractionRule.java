package com.mediflow.pharmacy.dictionary;

import com.mediflow.shared.domain.ConflictSeverity;
import java.io.Serializable;
import java.util.UUID;

/** {@code placeholder} = seeded curated data that still needs a licensed source. */
public record InteractionRule(
    UUID id,
    UUID genericAId,
    String genericAName,
    UUID genericBId,
    String genericBName,
    ConflictSeverity severity,
    String description,
    boolean placeholder)
    implements Serializable {}
