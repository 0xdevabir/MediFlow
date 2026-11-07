package com.mediflow.appointment.redalert;

import java.time.Instant;
import java.util.UUID;

/** Extra Red Alert / ER columns owned by T04 (native on {@code red_alert}). */
public record RedAlertExt(
    UUID assignedErUserId,
    UUID acknowledgedBy,
    String source,
    UUID handoverBy,
    Instant handoverAt,
    String handoverNote) {}
