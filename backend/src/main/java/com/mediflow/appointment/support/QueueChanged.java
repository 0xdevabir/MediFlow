package com.mediflow.appointment.support;

import java.time.LocalDate;
import java.util.UUID;

/** M2-internal: a doctor's queue for a day changed (pushes SSE updates after commit). */
public record QueueChanged(UUID doctorId, LocalDate queueDate) {}
