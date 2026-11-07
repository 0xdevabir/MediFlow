package com.mediflow.identity.password;

import com.mediflow.identity.login.LoginAttemptService;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.repo.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset by e-mailed token: 256-bit random token, only its SHA-256 stored, valid 30 minutes,
 * single use; a new request invalidates older tokens. The response never reveals whether the
 * account exists. A successful reset clears the lock-out.
 */
@Service
public class PasswordResetService {

  public static final Duration TOKEN_TTL = Duration.ofMinutes(30);

  /** Outcome of {@link #reset}. */
  public record ResetResult(boolean tokenValid, List<String> violations) {
    public boolean ok() {
      return tokenValid && violations.isEmpty();
    }
  }

  private final PasswordResetTokenRepository tokens;
  private final AppUserRepository users;
  private final PasswordHasher hasher;
  private final LoginAttemptService loginAttempts;
  private final NotificationPort notifications;
  private final AuditPort audit;
  private final ClockPort clock;
  private final MessageSource messages;
  private final SecureRandom random = new SecureRandom();

  public PasswordResetService(
      PasswordResetTokenRepository tokens,
      AppUserRepository users,
      PasswordHasher hasher,
      LoginAttemptService loginAttempts,
      NotificationPort notifications,
      AuditPort audit,
      ClockPort clock,
      MessageSource messages) {
    this.tokens = tokens;
    this.users = users;
    this.hasher = hasher;
    this.loginAttempts = loginAttempts;
    this.notifications = notifications;
    this.audit = audit;
    this.clock = clock;
    this.messages = messages;
  }

  /**
   * Issues a token and e-mails {@code resetUrlBase + "?token=" + token} to the user. Unknown or
   * disabled usernames are silently ignored.
   *
   * @return the raw token (for tests); callers must not show it to the requester
   */
  @Transactional
  public Optional<String> requestReset(String username, String resetUrlBase) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    Optional<AppUser> found = users.findByUsername(username.trim()).filter(AppUser::getEnabled);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    AppUser user = found.get();
    Instant now = clock.now();
    tokens.invalidateOutstanding(user.getId(), now);

    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    PasswordResetToken row = new PasswordResetToken();
    row.setUserId(user.getId());
    row.setTokenHash(sha256(token));
    row.setExpiresAt(now.plus(TOKEN_TTL));
    tokens.save(row);

    Locale locale = Locale.forLanguageTag(user.getLocale() == null ? "en" : user.getLocale());
    String link = resetUrlBase + "?token=" + token;
    notifications.notifyUser(
        user.getId(),
        "EMAIL",
        locale.toLanguageTag(),
        messages.getMessage("identity.password.reset.email.subject", null, "MediFlow password reset", locale),
        messages.getMessage(
            "identity.password.reset.email.body",
            new Object[] {link, TOKEN_TTL.toMinutes()},
            "Reset your MediFlow password: " + link,
            locale));
    audit.record(user.getId(), null, "PASSWORD_RESET_REQUESTED", "AppUser", user.getId(), Map.of());
    return Optional.of(token);
  }

  @Transactional(readOnly = true)
  public boolean isUsable(String token) {
    return find(token).map(t -> t.usableAt(clock.now())).orElse(false);
  }

  @Transactional
  public ResetResult reset(String token, String newPassword) {
    Instant now = clock.now();
    Optional<PasswordResetToken> row = find(token).filter(t -> t.usableAt(now));
    if (row.isEmpty()) {
      return new ResetResult(false, List.of());
    }
    Optional<AppUser> user = users.findById(row.get().getUserId());
    if (user.isEmpty()) {
      return new ResetResult(false, List.of());
    }
    List<String> violations = PasswordPolicy.violations(newPassword, user.get().getUsername());
    if (!violations.isEmpty()) {
      return new ResetResult(true, violations);
    }
    AppUser u = user.get();
    u.setPasswordHash(hasher.hash(newPassword));
    users.save(u);
    row.get().setUsedAt(now);
    tokens.save(row.get());
    tokens.invalidateOutstanding(u.getId(), now);
    loginAttempts.passwordChanged(u.getId());
    audit.record(u.getId(), null, "PASSWORD_RESET", "AppUser", u.getId(), Map.of());
    return new ResetResult(true, List.of());
  }

  private Optional<PasswordResetToken> find(String token) {
    if (token == null || token.isBlank() || token.length() > 128) {
      return Optional.empty();
    }
    return tokens.findByTokenHash(sha256(token));
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
