package com.mediflow.identity.mfa;

import com.mediflow.shared.port.ClockPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Step-up MFA for high-risk actions (T06 e-sign). Call {@link #requireFreshMfa()} at the start of
 * the service method: it passes if the signed-in user completed TOTP within the last 5 minutes in
 * this session, otherwise throws {@link MfaStepUpRequiredException} (web layer redirects to {@code
 * /identity/mfa/step-up?continue=<page>}, then back).
 */
@Service
public class MfaReauthService {

  private final ClockPort clock;

  public MfaReauthService(ClockPort clock) {
    this.clock = clock;
  }

  public void requireFreshMfa() {
    requireFreshMfa(MfaSession.FRESH_WINDOW);
  }

  public void requireFreshMfa(java.time.Duration maxAge) {
    if (!isFresh(maxAge)) {
      throw new MfaStepUpRequiredException();
    }
  }

  public boolean isFresh() {
    return isFresh(MfaSession.FRESH_WINDOW);
  }

  public boolean isFresh(java.time.Duration maxAge) {
    Optional<HttpSession> session = currentSession();
    String username = currentUsername();
    return session.isPresent()
        && username != null
        && MfaSession.isFresh(session.get(), username, clock.now(), maxAge);
  }

  public Optional<Instant> lastVerifiedAt() {
    return currentSession().flatMap(s -> MfaSession.verifiedAt(s, currentUsername()));
  }

  static Optional<HttpSession> currentSession() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      HttpServletRequest request = attrs.getRequest();
      return Optional.ofNullable(request.getSession(false));
    }
    return Optional.empty();
  }

  static String currentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || !auth.isAuthenticated() ? null : auth.getName();
  }
}
