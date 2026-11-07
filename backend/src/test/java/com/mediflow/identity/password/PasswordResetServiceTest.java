package com.mediflow.identity.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.identity.login.LoginAttemptService;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.repo.AppUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class PasswordResetServiceTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-07T06:00:00Z"));
  private final List<PasswordResetToken> rows = new ArrayList<>();
  private final NotificationPort notifications = mock(NotificationPort.class);
  private final LoginAttemptService attempts = mock(LoginAttemptService.class);
  private final PasswordHasher hasher = new PasswordHasher(false);
  private AppUser user;
  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    user = new AppUser();
    user.setId(UUID.randomUUID());
    user.setUsername("nurse");
    user.setEnabled(true);
    user.setPasswordHash(hasher.hash("Old-password-123"));
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findByUsername("nurse")).thenReturn(Optional.of(user));
    when(users.findByUsername("ghost")).thenReturn(Optional.empty());
    when(users.findById(user.getId())).thenReturn(Optional.of(user));

    PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    when(tokens.save(any()))
        .thenAnswer(
            i -> {
              PasswordResetToken t = i.getArgument(0);
              if (!rows.contains(t)) {
                rows.add(t);
              }
              return t;
            });
    when(tokens.findByTokenHash(anyString()))
        .thenAnswer(i -> rows.stream().filter(t -> t.getTokenHash().equals(i.getArgument(0))).findFirst());
    when(tokens.invalidateOutstanding(any(), any()))
        .thenAnswer(
            i -> {
              rows.stream()
                  .filter(t -> t.getUserId().equals(i.getArgument(0)) && t.getUsedAt() == null)
                  .forEach(t -> t.setUsedAt(i.getArgument(1)));
              return 0;
            });
    service =
        new PasswordResetService(
            tokens, users, hasher, attempts, notifications, mock(AuditPort.class), now::get,
            new StaticMessageSource());
  }

  @Test
  void unknownUserGetsNoTokenAndNoMail() {
    assertThat(service.requestReset("ghost", "http://x/reset")).isEmpty();
    verify(notifications, never()).notifyUser(any(), any(), any(), any(), any());
  }

  @Test
  void tokenIsEmailedHashedSingleUseAndUnlocks() {
    String token = service.requestReset("nurse", "http://x/reset").orElseThrow();
    verify(notifications).notifyUser(eq(user.getId()), eq("EMAIL"), any(), any(), contains(token));
    assertThat(rows.get(0).getTokenHash()).isNotEqualTo(token).hasSize(64);

    assertThat(service.reset(token, "short").violations()).isNotEmpty();
    assertThat(service.reset(token, "New-password-456").ok()).isTrue();
    assertThat(hasher.matches("New-password-456", user.getPasswordHash())).isTrue();
    verify(attempts).passwordChanged(user.getId());
    // second use refused
    assertThat(service.reset(token, "Another-pass-789").tokenValid()).isFalse();
  }

  @Test
  void tokenExpiresAfterThirtyMinutes() {
    String token = service.requestReset("nurse", "http://x/reset").orElseThrow();
    now.set(now.get().plus(Duration.ofMinutes(29)));
    assertThat(service.isUsable(token)).isTrue();
    now.set(now.get().plus(Duration.ofMinutes(2)));
    assertThat(service.isUsable(token)).isFalse();
    assertThat(service.reset(token, "New-password-456").tokenValid()).isFalse();
  }

  @Test
  void newRequestInvalidatesOlderToken() {
    String first = service.requestReset("nurse", "http://x/reset").orElseThrow();
    String second = service.requestReset("nurse", "http://x/reset").orElseThrow();
    assertThat(service.isUsable(first)).isFalse();
    assertThat(service.isUsable(second)).isTrue();
    assertThat(service.isUsable("garbage")).isFalse();
    assertThat(service.isUsable(null)).isFalse();
  }
}
