package com.mediflow.identity.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.identity.login.LoginAttemptService;
import com.mediflow.identity.login.LoginState;
import com.mediflow.identity.login.LoginStateRepository;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.MfaSecret;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.repo.AppUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MfaServiceTest {

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-11-07T06:00:10Z"));
  private final Map<UUID, LoginState> states = new HashMap<>();
  private final AtomicReference<MfaSecret> stored = new AtomicReference<>();
  private final TotpService totp = new TotpService();
  private final UUID userId = UUID.randomUUID();
  private AppUser user;
  private MfaService service;

  @BeforeEach
  void setUp() {
    user = new AppUser();
    user.setId(userId);
    user.setUsername("doctor");
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findById(userId)).thenReturn(Optional.of(user));

    IdentityMfaSecretRepository secrets = mock(IdentityMfaSecretRepository.class);
    when(secrets.findByUserId(userId)).thenAnswer(i -> Optional.ofNullable(stored.get()));
    when(secrets.save(any()))
        .thenAnswer(
            i -> {
              stored.set(i.getArgument(0));
              return i.getArgument(0);
            });

    LoginStateRepository loginStates = mock(LoginStateRepository.class);
    when(loginStates.findForUpdate(any()))
        .thenAnswer(i -> Optional.ofNullable(states.get(i.<UUID>getArgument(0))));
    when(loginStates.saveAndFlush(any()))
        .thenAnswer(
            i -> {
              LoginState s = i.getArgument(0);
              states.put(s.getUserId(), s);
              return s;
            });
    LoginAttemptService attempts =
        new LoginAttemptService(loginStates, users, now::get, mock(AuditPort.class));
    service = new MfaService(secrets, users, attempts, totp, now::get, mock(AuditPort.class));
  }

  private String currentCode(String secret) {
    return totp.codeAt(secret, TotpService.step(now.get()));
  }

  @Test
  void enrolStoresEncryptedSecretAndConfirms() {
    String secret = service.pendingEnrolmentSecret(userId);
    assertThat(stored.get().getSecretEnc()).isNotEqualTo(secret).doesNotContain(secret);
    assertThat(service.pendingEnrolmentSecret(userId)).isEqualTo(secret);
    assertThat(service.confirmEnrolment(userId, "000000")).isNotEqualTo(MfaService.VerifyResult.OK);
    assertThat(service.confirmEnrolment(userId, currentCode(secret))).isEqualTo(MfaService.VerifyResult.OK);
    assertThat(stored.get().getVerified()).isTrue();
    assertThat(user.getMfaEnabled()).isTrue();
    assertThatThrownBy(() -> service.pendingEnrolmentSecret(userId)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void verifyRejectsReplayAndLocksAfterFiveWrongCodes() {
    String secret = service.pendingEnrolmentSecret(userId);
    String first = currentCode(secret);
    assertThat(service.confirmEnrolment(userId, first)).isEqualTo(MfaService.VerifyResult.OK);
    // same code again (replay) is refused
    assertThat(service.verify(userId, first)).isEqualTo(MfaService.VerifyResult.INVALID);

    now.set(now.get().plusSeconds(30));
    assertThat(service.verify(userId, currentCode(secret))).isEqualTo(MfaService.VerifyResult.OK);

    String wrong = "999999".equals(currentCode(secret)) ? "888888" : "999999";
    for (int i = 0; i < 4; i++) {
      assertThat(service.verify(userId, wrong)).isEqualTo(MfaService.VerifyResult.INVALID);
    }
    assertThat(service.verify(userId, wrong)).isEqualTo(MfaService.VerifyResult.LOCKED);
    now.set(now.get().plusSeconds(60));
    assertThat(service.verify(userId, currentCode(secret))).isEqualTo(MfaService.VerifyResult.LOCKED);
    now.set(now.get().plus(Duration.ofMinutes(16)));
    assertThat(service.verify(userId, currentCode(secret))).isEqualTo(MfaService.VerifyResult.OK);
  }

  @Test
  void requiredRoles() {
    assertThat(MfaService.requiresMfa(java.util.Set.of(com.mediflow.shared.domain.Role.ROLE_DOCTOR), false)).isTrue();
    assertThat(MfaService.requiresMfa(java.util.Set.of(com.mediflow.shared.domain.Role.ROLE_NURSE), false)).isFalse();
    assertThat(MfaService.requiresMfa(java.util.Set.of(com.mediflow.shared.domain.Role.ROLE_PATIENT), true)).isTrue();
  }
}
