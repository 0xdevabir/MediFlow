package com.mediflow.identity.login;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.AppUserRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records login outcomes and answers "is this account locked?". */
@Service
public class LoginAttemptService {

  private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

  private final LoginStateRepository states;
  private final AppUserRepository users;
  private final ClockPort clock;
  private final AuditPort audit;

  public LoginAttemptService(
      LoginStateRepository states, AppUserRepository users, ClockPort clock, AuditPort audit) {
    this.states = states;
    this.users = users;
    this.clock = clock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public boolean isLocked(UUID userId) {
    return states.findById(userId).map(s -> LockoutPolicy.isLocked(s, clock.now())).orElse(false);
  }

  /** Counts a wrong password; unknown usernames are ignored (no enumeration side channel). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onFailure(String username) {
    users
        .findByUsername(username)
        .ifPresent(
            user -> {
              LoginState state = lockedState(user.getId());
              boolean lockedNow = LockoutPolicy.recordFailure(state, clock.now());
              states.save(state);
              audit.record(
                  user.getId(),
                  null,
                  lockedNow ? "LOGIN_LOCKED" : "LOGIN_FAILED",
                  "AppUser",
                  user.getId(),
                  Map.of("failedAttempts", state.getFailedAttempts()));
              if (lockedNow) {
                log.warn("Account {} locked after {} failed logins", username, state.getFailedAttempts());
              }
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onSuccess(String username) {
    users
        .findByUsername(username)
        .map(AppUser::getId)
        .ifPresent(
            id -> {
              states
                  .findForUpdate(id)
                  .ifPresent(
                      s -> {
                        if (s.getFailedAttempts() > 0 || s.getLockedUntil() != null) {
                          LockoutPolicy.recordSuccess(s);
                        }
                      });
              audit.record(id, null, "LOGIN_SUCCESS", "AppUser", id, Map.of());
            });
  }

  /** Admin unlock / password reset. */
  @Transactional
  public void unlock(UUID userId) {
    states.findForUpdate(userId).ifPresent(LockoutPolicy::recordSuccess);
  }

  /** After a password reset / change: clear the lock and remember when it changed. */
  @Transactional
  public void passwordChanged(UUID userId) {
    LoginState state = lockedState(userId);
    LockoutPolicy.recordSuccess(state);
    state.setPasswordChangedAt(clock.now());
    states.save(state);
  }

  /** Row for the user, created on first use, locked for update. */
  @Transactional(propagation = Propagation.MANDATORY)
  public LoginState lockedState(UUID userId) {
    return states.findForUpdate(userId).orElseGet(() -> states.saveAndFlush(new LoginState(userId)));
  }
}
