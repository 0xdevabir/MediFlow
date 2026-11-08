package com.mediflow.pharmacy.config;

import com.mediflow.pharmacy.dispensing.DefaultPatientIdentityVerifier;
import com.mediflow.pharmacy.dispensing.PatientIdentityPort;
import com.mediflow.shared.port.PatientQrPort;
import com.mediflow.shared.repo.PatientRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PharmacyIdentityConfig {

  @Bean
  @ConditionalOnMissingBean(PatientIdentityPort.class)
  PatientIdentityPort patientIdentityPort(
      PatientRepository patients, ObjectProvider<PatientQrPort> qrPort) {
    return new DefaultPatientIdentityVerifier(patients, qrPort);
  }
}
