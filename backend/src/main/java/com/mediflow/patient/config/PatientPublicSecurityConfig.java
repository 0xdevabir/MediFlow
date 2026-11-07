package com.mediflow.patient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Public chain for self sign-up. Kept separate so T00's deny-by-default chain does not need
 * editing (request filed for {@code /patients} placeholder removal).
 */
@Configuration
public class PatientPublicSecurityConfig {

  @Bean
  @Order(1)
  SecurityFilterChain patientPublicSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatchers(
            matchers -> matchers.requestMatchers("/patients/register", "/patients/register/**"))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .headers(Customizer.withDefaults());
    return http.build();
  }
}

