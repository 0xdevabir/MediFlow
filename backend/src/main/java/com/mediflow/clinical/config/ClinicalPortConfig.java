package com.mediflow.clinical.config;

import com.mediflow.clinical.consultation.port.BridgedClinicalAccess;
import com.mediflow.clinical.consultation.port.ClinicalAccess;
import com.mediflow.clinical.consultation.port.DefaultClinicalAccess;
import com.mediflow.clinical.consultation.repo.ClinicalAppointmentRepository;
import com.mediflow.clinical.consultation.repo.ClinicalEncounterRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPractitionerRepository;
import com.mediflow.identity.access.AccessPolicy;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.web.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
      ClinicalAppointmentRepository appointments,
      ObjectProvider<AccessPolicy> accessPolicies) {
    DefaultClinicalAccess legacy =
        new DefaultClinicalAccess(
            currentUser, patientAccess, practitioners, encounters, appointments);
    AccessPolicy policy = accessPolicies.getIfAvailable();
    return policy == null ? legacy : new BridgedClinicalAccess(policy, legacy);
  }
}
