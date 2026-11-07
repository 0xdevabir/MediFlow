package com.mediflow.identity.mfa;

import com.mediflow.identity.login.LockoutPolicy;
import com.mediflow.identity.login.LoginAttemptService;
import com.mediflow.identity.login.LoginState;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.MfaSecret;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.encryption.AesGcmAttributeConverter;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.AppUserRepository;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TOTP enrolment and verification. Secrets are AES-256-GCM encrypted in {@code mfa_secret}. */
@Service
public class MfaService {

  /** Roles for which MFA is mandatory (R&D §12, Technical Table 16, plus clinical approver). */
  public static final Set<Role> MFA_REQUIRED_ROLES =
      EnumSet.of(Role.ROLE_DOCTOR, Role.ROLE_PHARMACIST, Role.ROLE_ADMIN, Role.ROLE_CLINICAL_APPROVER);

  public enum VerifyResult {
    OK,
    INVALID,
    LOCKED
  }

  private final IdentityMfaSecretRepository secrets;
  private final AppUserRepository users;
  private final LoginAttemptService loginAttempts;
  private final TotpService totp;
  private final ClockPort clock;
  private final AuditPort audit;
  private final AesGcmAttributeConverter crypto = new AesGcmAttributeConverter();

  public MfaService(
      IdentityMfaSecretRepository secrets,
      AppUserRepository users,
      LoginAttemptService loginAttempts,
      TotpService totp,
      ClockPort clock,
      AuditPort audit) {
    this.secrets = secrets;
    this.users = users;
    this.loginAttempts = loginAttempts;
    this.totp = totp;
    this.clock = clock;
    this.audit = audit;
  }

  public static boolean requiresMfa(Set<Role> roles, boolean userOptedIn) {
    return userOptedIn || roles.stream().anyMatch(MFA_REQUIRED_ROLES::contains);
  }

  @Transactional(readOnly = true)
  public boolean hasVerifiedSecret(UUID userId) {
    return secrets.findByUserId(userId).map(MfaSecret::getVerified).orElse(false);
  }

  /**
   * Returns the plaintext secret to show once during enrolment (creating it if needed). Refused once
   * a verified secret exists: re-enrolment needs an admin {@link #reset}.
   */
  @Transactional
  public String pendingEnrolmentSecret(UUID userId) {
    Optional<MfaSecret> existing = secrets.findByUserId(userId);
    if (existing.isPresent()) {
      if (existing.get().getVerified()) {
        throw new IllegalStateException("MFA already enrolled");
      }
      return crypto.convertToEntityAttribute(existing.get().getSecretEnc());
    }
    String secret = totp.newSecret();
    MfaSecret row = new MfaSecret();
    row.setUserId(userId);
    row.setSecretEnc(crypto.convertToDatabaseColumn(secret));
    row.setVerified(false);
    secrets.save(row);
    return secret;
  }

  /** Confirms enrolment with a first code; marks the secret verified and the user MFA-enabled. */
  @Transactional(noRollbackFor = Exception.class)
  public VerifyResult confirmEnrolment(UUID userId, String code) {
    MfaSecret row = secrets.findByUserId(userId).orElse(null);
    if (row == null || row.getVerified()) {
      return VerifyResult.INVALID;
    }
    VerifyResult result = check(userId, row, code, "MFA_ENROLLED");
    if (result == VerifyResult.OK) {
      row.setVerified(true);
      secrets.save(row);
      users
          .findById(userId)
          .ifPresent(
              u -> {
                u.setMfaEnabled(true);
                users.save(u);
              });
    }
    return result;
  }

  /** Verifies a login / step-up code against the enrolled secret. */
  @Transactional(noRollbackFor = Exception.class)
  public VerifyResult verify(UUID userId, String code) {
    MfaSecret row = secrets.findByUserId(userId).filter(MfaSecret::getVerified).orElse(null);
    if (row == null) {
      return VerifyResult.INVALID;
    }
    return check(userId, row, code, "MFA_VERIFIED");
  }

  /** Admin: remove the secret so the user must enrol again at next login. */
  @Transactional
  public void reset(UUID userId, UUID actorUserId) {
    secrets.deleteByUserId(userId);
    users
        .findById(userId)
        .ifPresent(
            u -> {
              u.setMfaEnabled(false);
              users.save(u);
            });
    audit.record(actorUserId, null, "MFA_RESET", "AppUser", userId, Map.of());
  }

  private VerifyResult check(UUID userId, MfaSecret row, String code, String successAction) {
    LoginState state = loginAttempts.lockedState(userId);
    var now = clock.now();
    if (LockoutPolicy.isLocked(state, now)) {
      return VerifyResult.LOCKED;
    }
    String secret = crypto.convertToEntityAttribute(row.getSecretEnc());
    OptionalLong step = totp.matchingStep(secret, code, now, state.getMfaLastUsedStep());
    if (step.isPresent()) {
      state.setMfaLastUsedStep(step.getAsLong());
      LockoutPolicy.recordSuccess(state);
      audit.record(userId, null, successAction, "AppUser", userId, Map.of());
      return VerifyResult.OK;
    }
    boolean locked = LockoutPolicy.recordFailure(state, now);
    audit.record(
        userId,
        null,
        locked ? "MFA_LOCKED" : "MFA_FAILED",
        "AppUser",
        userId,
        Map.of("failedAttempts", state.getFailedAttempts()));
    return locked ? VerifyResult.LOCKED : VerifyResult.INVALID;
  }

  @Transactional(readOnly = true)
  public Optional<AppUser> user(String username) {
    return users.findByUsername(username);
  }
}
