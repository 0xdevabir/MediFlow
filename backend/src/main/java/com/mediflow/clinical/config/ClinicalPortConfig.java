package com.mediflow.clinical.config;

import com.mediflow.clinical.consultation.port.ClinicalAccess;
import com.mediflow.clinical.consultation.port.DefaultClinicalAccess;
import com.mediflow.clinical.consultation.port.LabOrderPort;
import com.mediflow.clinical.consultation.port.NoOpLabOrderPort;
import com.mediflow.clinical.consultation.repo.ClinicalAppointmentRepository;
import com.mediflow.clinical.consultation.repo.ClinicalEncounterRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPractitionerRepository;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.web.CurrentUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClinicalPortConfig {

  @Bean
  @ConditionalOnMissingBean(ClinicalAccess.class)
  ClinicalAccess clinicalAccess(
      CurrentUser currentUser,
      PatientAccessPort patientAccess,
      ClinicalPractitionerRepository practitioners,
      ClinicalEncounterRepository encounters,
      ClinicalAppointmentRepository appointments) {
    return new DefaultClinicalAccess(
        currentUser, patientAccess, practitioners, encounters, appointments);
  }

  @Bean
  @ConditionalOnMissingBean(LabOrderPort.class)
  LabOrderPort labOrderPort(
      ApplicationEventPublisher events, CurrentUser currentUser, ClockPort clock) {
    return new NoOpLabOrderPort(events, currentUser, clock);
  }
}
