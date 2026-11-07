package com.mediflow.identity.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.identity.password.PasswordHasher;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.repo.AppUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;

/** Lock-out: 5 failed logins → locked 15 minutes, even with the right password. */
class LockoutTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-07T06:00:00Z"));
  private final Map<UUID, LoginState> store = new HashMap<>();
  private AppUser user;
  private IdentityAuthenticationProvider provider;
  private LoginAttemptService attempts;

  @BeforeEach
  void setUp() {
    PasswordHasher hasher = new PasswordHasher(false);
    user = new AppUser();
    user.setId(UUID.randomUUID());
    user.setUsername("doctor");
    user.setPasswordHash(hasher.hash("Correct-Horse-42"));
    user.setEnabled(true);
    user.setRoles(Set.of(Role.ROLE_DOCTOR));

    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findByUsername("doctor")).thenReturn(Optional.of(user));
    when(users.findByUsername("ghost")).thenReturn(Optional.empty());

    LoginStateRepository states = mock(LoginStateRepository.class);
    when(states.findById(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
    when(states.findForUpdate(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
    when(states.saveAndFlush(any()))
        .thenAnswer(
            i -> {
              LoginState s = i.getArgument(0);
              store.put(s.getUserId(), s);
              return s;
            });
    when(states.save(any())).thenAnswer(i -> i.getArgument(0));

    attempts = new LoginAttemptService(states, users, now::get, mock(AuditPort.class));
    provider = new IdentityAuthenticationProvider(users, attempts, hasher, mock(TransactionTemplate.class));
  }

  private Authentication login(String user, String pw) {
    return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(user, pw));
  }

  @Test
  void fiveFailuresLockForFifteenMinutes() {
    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> login("doctor", "wrong")).isInstanceOf(BadCredentialsException.class);
    }
    assertThat(attempts.isLocked(user.getId())).isFalse();
    // 4 failures then success resets the counter
    assertThat(login("doctor", "Correct-Horse-42").isAuthenticated()).isTrue();
    assertThat(store.get(user.getId()).getFailedAttempts()).isZero();

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> login("doctor", "wrong")).isInstanceOf(BadCredentialsException.class);
    }
    assertThat(attempts.isLocked(user.getId())).isTrue();
    // correct password is refused while locked
    assertThatThrownBy(() -> login("doctor", "Correct-Horse-42")).isInstanceOf(LockedException.class);

    now.set(now.get().plus(Duration.ofMinutes(14)));
    assertThatThrownBy(() -> login("doctor", "Correct-Horse-42")).isInstanceOf(LockedException.class);

    now.set(now.get().plus(Duration.ofMinutes(2)));
    assertThat(attempts.isLocked(user.getId())).isFalse();
    assertThat(login("doctor", "Correct-Horse-42").isAuthenticated()).isTrue();
  }

  @Test
  void failuresAfterExpiredLockStartFresh() {
    LoginState s = new LoginState(user.getId());
    for (int i = 0; i < 5; i++) {
      LockoutPolicy.recordFailure(s, now.get());
    }
    assertThat(LockoutPolicy.isLocked(s, now.get())).isTrue();
    Instant later = now.get().plus(LockoutPolicy.LOCK_DURATION);
    assertThat(LockoutPolicy.isLocked(s, later)).isFalse();
    assertThat(LockoutPolicy.recordFailure(s, later)).isFalse();
    assertThat(s.getFailedAttempts()).isEqualTo(1);
  }

  @Test
  void unknownUserDoesNotCreateState() {
    assertThatThrownBy(() -> login("ghost", "x")).isInstanceOf(BadCredentialsException.class);
    assertThat(store).isEmpty();
  }

  @Test
  void unlockClearsLock() {
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> login("doctor", "wrong")).isInstanceOf(BadCredentialsException.class);
    }
    attempts.unlock(user.getId());
    assertThat(login("doctor", "Correct-Horse-42").isAuthenticated()).isTrue();
  }
}
