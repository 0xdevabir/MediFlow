package com.mediflow.identity.mfa;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Session attributes of the MFA step. Values are Serializable (Spring Session / Redis).
 *
 * <p>Tests of other modules can simulate a fresh MFA with {@code
 * session.setAttribute(MfaSession.VERIFIED_AT, Instant.now());
 * session.setAttribute(MfaSession.VERIFIED_FOR, "doctor");}
 */
public final class MfaSession {

  public static final String VERIFIED_AT = "identity.mfa.verifiedAt";
  public static final String VERIFIED_FOR = "identity.mfa.verifiedFor";
  static final String REQUIRED_FOR = "identity.mfa.requiredFor";
  static final String REQUIRED = "identity.mfa.required";

  /** Step-up window for e-sign and other high-risk actions. */
  public static final Duration FRESH_WINDOW = Duration.ofMinutes(5);

  private MfaSession() {}

  public static Optional<Instant> verifiedAt(HttpSession session, String username) {
    if (session == null || username == null) {
      return Optional.empty();
    }
    Object who = session.getAttribute(VERIFIED_FOR);
    Object at = session.getAttribute(VERIFIED_AT);
    if (username.equals(who) && at instanceof Instant instant) {
      return Optional.of(instant);
    }
    return Optional.empty();
  }

  public static void markVerified(HttpSession session, String username, Instant now) {
    session.setAttribute(VERIFIED_FOR, username);
    session.setAttribute(VERIFIED_AT, now);
  }

  public static boolean isFresh(HttpSession session, String username, Instant now) {
    return isFresh(session, username, now, FRESH_WINDOW);
  }

  public static boolean isFresh(HttpSession session, String username, Instant now, Duration maxAge) {
    Duration window = maxAge == null ? FRESH_WINDOW : maxAge;
    return verifiedAt(session, username)
        .map(at -> !at.isAfter(now) && Duration.between(at, now).compareTo(window) <= 0)
        .orElse(false);
  }
}
