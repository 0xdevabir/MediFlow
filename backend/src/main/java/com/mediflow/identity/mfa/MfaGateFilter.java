package com.mediflow.identity.mfa;

import com.mediflow.identity.access.AccessPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Second login step. Runs after the Spring Security chain: a signed-in user whose role needs MFA (or
 * who opted in) and who has not completed TOTP in this session is sent to enrolment (no secret yet)
 * or verification; JSON API calls get 401. Deny by default: every path except the MFA pages,
 * logout, static assets and error pages is gated.
 */
@Component
public class MfaGateFilter extends OncePerRequestFilter {

  static final String ENROL = "/identity/mfa/enrol";
  static final String VERIFY = "/identity/mfa/verify";

  private static final List<String> OPEN_PREFIXES =
      List.of(
          "/identity/mfa/",
          "/identity/password/",
          "/login",
          "/logout",
          "/error",
          "/css/",
          "/js/",
          "/images/",
          "/webjars/",
          "/favicon",
          "/actuator/health");

  private final MfaEnforcement enforcement;
  private final MfaRequirementResolver requirement;

  public MfaGateFilter(MfaEnforcement enforcement, MfaRequirementResolver requirement) {
    this.enforcement = enforcement;
    this.requirement = requirement;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!enforcement.enforced()) {
      return true;
    }
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return OPEN_PREFIXES.stream().anyMatch(path::startsWith);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      chain.doFilter(request, response);
      return;
    }
    HttpSession session = request.getSession(false);
    String username = auth.getName();
    if (MfaSession.verifiedAt(session, username).isPresent()) {
      chain.doFilter(request, response);
      return;
    }
    MfaRequirementResolver.Requirement req =
        requirement.resolve(session, username, AccessPolicy.roles(auth));
    if (req == MfaRequirementResolver.Requirement.NONE) {
      chain.doFilter(request, response);
      return;
    }
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (path.startsWith("/api/")) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MFA required");
      return;
    }
    String target = req == MfaRequirementResolver.Requirement.ENROL ? ENROL : VERIFY;
    response.sendRedirect(request.getContextPath() + target);
  }
}
