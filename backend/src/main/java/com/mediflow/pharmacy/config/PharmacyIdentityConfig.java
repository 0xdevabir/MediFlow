package com.mediflow.pharmacy.config;

import com.mediflow.pharmacy.dispensing.DefaultPatientIdentityVerifier;
import com.mediflow.pharmacy.dispensing.PatientIdentityPort;
import com.mediflow.shared.repo.PatientRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PharmacyIdentityConfig {

  @Bean
  @ConditionalOnMissingBean(PatientIdentityPort.class)
  PatientIdentityPort patientIdentityPort(PatientRepository patients) {
    return new DefaultPatientIdentityVerifier(patients);
  }
}
