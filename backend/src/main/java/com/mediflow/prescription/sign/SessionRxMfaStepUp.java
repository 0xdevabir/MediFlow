package com.mediflow.prescription.sign;

import com.mediflow.prescription.support.RxExceptions.MfaStepUpRequiredException;
import com.mediflow.shared.port.ClockPort;
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
 * Safe default MFA step-up (T01 session contract). Replace by a {@link RxMfaStepUp} bean that
 * delegates to T01 {@code MfaReauthService} when identity is on the classpath.
 */
@Component
@ConditionalOnMissingBean(RxMfaStepUp.class)
public class SessionRxMfaStepUp implements RxMfaStepUp {

  public static final String VERIFIED_AT = "identity.mfa.verifiedAt";
  public static final String VERIFIED_FOR = "identity.mfa.verifiedFor";
  public static final Duration FRESH_WINDOW = Duration.ofMinutes(5);

  private final ClockPort clock;

  public SessionRxMfaStepUp(ClockPort clock) {
    this.clock = clock;
  }

  @Override
  public void requireFreshMfa() {
    if (!isFresh()) {
      throw new MfaStepUpRequiredException();
    }
  }

  @Override
  public boolean isFresh() {
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
    return !instant.isAfter(now) && Duration.between(instant, now).compareTo(FRESH_WINDOW) <= 0;
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
