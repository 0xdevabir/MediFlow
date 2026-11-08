package com.mediflow.patient.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Safe default until a shared appointment port supplies the next-visit lookup. */
@Component
public class EmptyPatientAppointmentGlancePort implements PatientAppointmentGlancePort {

  @Override
  public Optional<NextVisit> nextUpcoming(UUID patientId) {
    return Optional.empty();
  }
}
