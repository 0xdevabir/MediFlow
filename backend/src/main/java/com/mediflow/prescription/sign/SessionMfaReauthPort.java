package com.mediflow.prescription.sign;

import com.mediflow.prescription.support.RxExceptions.MfaStepUpRequiredException;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.MfaReauthPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Safe default {@link MfaReauthPort} until V2-02 wires identity's step-up. Uses the same session
 * keys as identity MFA so a completed step-up is visible either way.
 */
@Component
@ConditionalOnMissingBean(MfaReauthPort.class)
public class SessionMfaReauthPort implements MfaReauthPort {

  public static final String VERIFIED_AT = "identity.mfa.verifiedAt";
  public static final String VERIFIED_FOR = "identity.mfa.verifiedFor";

  private final ClockPort clock;

  public SessionMfaReauthPort(ClockPort clock) {
    this.clock = clock;
  }

  @Override
  public void requireFreshMfa(Duration maxAge) {
    if (!isFresh(maxAge == null ? Duration.ofMinutes(5) : maxAge)) {
      throw new MfaStepUpRequiredException();
    }
  }

  boolean isFresh(Duration maxAge) {
    Optional<HttpSession> session = currentSession();
    String username = currentUsername();
    if (session.isEmpty() || username == null) {
      return false;
    }
    Object who = session.get().getAttribute(VERIFIED_FOR);
    Object at = session.get().getAttribute(VERIFIED_AT);
    if (!username.equals(who) || !(at instanceof Instant instant)) {
      return false;
    }
    Instant now = clock.now();
    return !instant.isAfter(now) && Duration.between(instant, now).compareTo(maxAge) <= 0;
  }

  private static Optional<HttpSession> currentSession() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return Optional.ofNullable(attrs.getRequest().getSession(false));
    }
    return Optional.empty();
  }

  private static String currentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || !auth.isAuthenticated() ? null : auth.getName();
  }

  /** Test helper: mark MFA fresh in the current session. */
  public static void markFresh(HttpServletRequest request, String username, Instant now) {
    HttpSession session = request.getSession(true);
    session.setAttribute(VERIFIED_FOR, username);
    session.setAttribute(VERIFIED_AT, now);
  }
}
