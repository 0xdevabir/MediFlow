package com.mediflow.directory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Public (no-login) read access for the hospital / doctor directory. Admin CRUD stays on the main
 * T00 chain under {@code /directory/admin/**}.
 */
@Configuration
public class DirectoryPublicSecurityConfig {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 20)
  SecurityFilterChain directoryPublicChain(HttpSecurity http) throws Exception {
    http.securityMatcher(
            new OrRequestMatcher(
                new AntPathRequestMatcher("/directory", "GET"),
                new AntPathRequestMatcher("/directory/", "GET"),
                new AntPathRequestMatcher("/directory/search", "GET"),
                new AntPathRequestMatcher("/directory/tests", "GET"),
                new AntPathRequestMatcher("/directory/packages", "GET")))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .headers(
            h ->
                h.referrerPolicy(
                        r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                    .contentSecurityPolicy(
                        c ->
                            c.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';"
                                    + " img-src 'self' data:; frame-ancestors 'none'; form-action 'self';"
                                    + " base-uri 'self'")))
        .requestCache(rc -> rc.disable());
    // CSRF stays enabled (default) so public forms remain safe if added later.
    return http.build();
  }
}
