package com.mediflow.patient.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Safe default until the appointment module registers {@link PatientAppointmentGlancePort}. */
@Component
@ConditionalOnMissingBean(PatientAppointmentGlancePort.class)
public class EmptyPatientAppointmentGlancePort implements PatientAppointmentGlancePort {

  @Override
  public Optional<NextVisit> nextUpcoming(UUID patientId) {
    return Optional.empty();
  }
}
