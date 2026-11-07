package com.mediflow.fhir.config;

import com.mediflow.fhir.auth.FhirBearerTokenFilter;
import com.mediflow.fhir.auth.FhirTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class FhirSecurityConfig {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 25)
  SecurityFilterChain fhirSecurityFilterChain(HttpSecurity http, FhirTokenService tokens)
      throws Exception {
    http.securityMatcher("/fhir/r4/**")
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/fhir/r4/metadata")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/fhir/r4/oauth/token")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .httpBasic(Customizer.withDefaults())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .addFilterBefore(new FhirBearerTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
