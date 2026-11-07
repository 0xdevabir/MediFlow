package com.mediflow.identity.password;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Extra filter chain for the public password-reset pages only ({@code /identity/password/**}); the
 * main T00 chain still covers everything else. CSRF stays on (session token repository).
 */
@Configuration
public class IdentityPublicSecurityConfig {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 10)
  SecurityFilterChain identityPublicChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/identity/password/**")
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .headers(
            h ->
                h.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .contentSecurityPolicy(
                        c ->
                            c.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';"
                                    + " img-src 'self' data:; frame-ancestors 'none'; form-action 'self'")))
        .requestCache(rc -> rc.disable());
    return http.build();
  }
}
