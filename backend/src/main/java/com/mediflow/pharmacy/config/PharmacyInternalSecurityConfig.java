package com.mediflow.pharmacy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Separate chain for service-to-service dictionary export. Does not weaken the main T00 chain;
 * only {@code /api/internal/dictionary/**} is matched. Auth is a shared secret header (not a
 * session cookie), so CSRF is off for this matcher only.
 */
@Configuration
public class PharmacyInternalSecurityConfig {

  public static final String HEADER = "X-Mediflow-Internal-Key";

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 20)
  SecurityFilterChain pharmacyInternalChain(HttpSecurity http, PharmacyProperties props)
      throws Exception {
    http.securityMatcher("/api/internal/dictionary/**")
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.disable())
        .addFilterBefore(new InternalKeyFilter(props), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  static final class InternalKeyFilter extends OncePerRequestFilter {

    private final PharmacyProperties props;

    InternalKeyFilter(PharmacyProperties props) {
      this.props = props;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      String expected = props.getInternalApiKey();
      String provided = request.getHeader(HEADER);
      if (expected == null
          || expected.isBlank()
          || provided == null
          || !constantTimeEquals(expected, provided)) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\"}");
        return;
      }
      chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
      byte[] x = a.getBytes(StandardCharsets.UTF_8);
      byte[] y = b.getBytes(StandardCharsets.UTF_8);
      return MessageDigest.isEqual(x, y);
    }
  }
}
