package com.mediflow.notification.repo;

import java.time.Instant;
import java.util.UUID;

/** Projection of an active (non-cancelled) appointment tied to a directory slot. */
public record BookedAppointmentRef(UUID appointmentId, UUID patientId, Instant startsAt) {}
