package com.mediflow.config;

import com.mediflow.identity.password.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    CookieCsrfTokenRepository csrfCookies = CookieCsrfTokenRepository.withHttpOnlyFalse();
    // SameSite=Strict on the CSRF cookie; HttpOnly remains false so fetch/SSE helpers can read
    // XSRF-TOKEN. Thymeleaf forms continue to use the hidden ${_csrf.token} field.
    csrfCookies.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));

    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/css/**",
                        "/js/**",
                        "/webjars/**",
                        "/images/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/api/csrf",
                        "/login",
                        "/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
        .csrf(csrf -> csrf.csrfTokenRepository(csrfCookies))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; "
                                    + "script-src 'self'; "
                                    + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                    + "font-src 'self' https://fonts.gstatic.com data:; "
                                    + "img-src 'self' data:; "
                                    + "frame-ancestors 'none'; form-action 'self'; base-uri 'self'"))
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
        .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()));

    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder(PasswordHasher hasher) {
    return hasher.encoder();
  }
}
