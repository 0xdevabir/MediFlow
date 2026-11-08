package com.mediflow.patient.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal read of the patient's next appointment. Appointment module owns the real data; until a
 * shared port exists, the patient module uses a safe empty default so the dashboard still renders.
 */
public interface PatientAppointmentGlancePort {

  record NextVisit(UUID appointmentId, Instant when, String summary) {}

  Optional<NextVisit> nextUpcoming(UUID patientId);
}
