package com.mediflow.fhir.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accepts `Authorization: Bearer <token>` issued by `/fhir/r4/oauth/token`. */
public class FhirBearerTokenFilter extends OncePerRequestFilter {

  private final FhirTokenService tokens;

  public FhirBearerTokenFilter(FhirTokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      String header = request.getHeader(HttpHeaders.AUTHORIZATION);
      if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
        tokens
            .authenticateBearer(header.substring(7).strip())
            .ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
      }
    }
    filterChain.doFilter(request, response);
  }
}
