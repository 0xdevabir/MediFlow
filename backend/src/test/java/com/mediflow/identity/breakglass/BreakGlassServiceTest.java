package com.mediflow.identity.breakglass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.shared.domain.BreakGlassAccess;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class BreakGlassServiceTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-07T06:00:00Z"));
  private final List<BreakGlassAccess> rows = new ArrayList<>();
  private final List<BreakGlassReview> reviewRows = new ArrayList<>();
  private final NotificationPort notifications = mock(NotificationPort.class);
  private final AuditPort audit = mock(AuditPort.class);
  private final UUID doctor = UUID.randomUUID();
  private final UUID admin1 = UUID.randomUUID();
  private final UUID admin2 = UUID.randomUUID();
  private final UUID patient = UUID.randomUUID();
  private final AtomicReference<UUID> actor = new AtomicReference<>(doctor);
  private BreakGlassService service;

  @BeforeEach
  void setUp() {
    IdentityBreakGlassRepository accesses = mock(IdentityBreakGlassRepository.class);
    when(accesses.save(any()))
        .thenAnswer(
            i -> {
              BreakGlassAccess b = i.getArgument(0);
              if (b.getId() == null) {
                b.setId(UUID.randomUUID());
                rows.add(b);
              }
              return b;
            });
    when(accesses.findActive(any(), any(), any()))
        .thenAnswer(
            i ->
                rows.stream()
                    .filter(
                        b ->
                            b.getUserId().equals(i.getArgument(0))
                                && b.getPatientId().equals(i.getArgument(1))
                                && BreakGlassService.isActive(b, i.getArgument(2)))
                    .toList());
    when(accesses.findById(any()))
        .thenAnswer(i -> rows.stream().filter(b -> b.getId().equals(i.getArgument(0))).findFirst());
    when(accesses.findTop200ByOrderByStartedAtDesc()).thenAnswer(i -> List.copyOf(rows));

    BreakGlassReviewRepository reviews = mock(BreakGlassReviewRepository.class);
    when(reviews.save(any()))
        .thenAnswer(
            i -> {
              reviewRows.add(i.getArgument(0));
              return i.getArgument(0);
            });
    when(reviews.findByBreakGlassId(any()))
        .thenAnswer(
            i -> reviewRows.stream().filter(r -> r.getBreakGlassId().equals(i.getArgument(0))).findFirst());

    IdentityAdminDirectory directory = mock(IdentityAdminDirectory.class);
    when(directory.patientExists(patient)).thenReturn(true);
    when(directory.enabledUsersWithRole(Role.ROLE_ADMIN))
        .thenReturn(
            List.of(
                new IdentityAdminDirectory.Recipient(admin1, "en"),
                new IdentityAdminDirectory.Recipient(admin2, "bn")));
    when(directory.mrns(any())).thenReturn(Map.of(patient, "MRN-1"));
    when(directory.usernames(any())).thenReturn(Map.of(doctor, "doctor"));

    CurrentUser current = mock(CurrentUser.class);
    when(current.requireUserId()).thenAnswer(i -> actor.get());
    when(current.userId()).thenAnswer(i -> Optional.of(actor.get()));
    when(current.username()).thenReturn(Optional.of("doctor"));

    service =
        new BreakGlassService(
            accesses, reviews, directory, notifications, audit, now::get, current,
            new StaticMessageSource());
  }

  @Test
  void reasonIsMandatory() {
    assertThatThrownBy(() -> service.activate(patient, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.activate(patient, "  short  ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.activate(UUID.randomUUID(), "Unconscious patient in ER"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(rows).isEmpty();
  }

  @Test
  void activationLastsSixtyMinutesAndNotifiesAdmins() {
    BreakGlassAccess a = service.activate(patient, "Unconscious patient in ER");
    assertThat(a.getExpiresAt()).isEqualTo(now.get().plus(Duration.ofMinutes(60)));
    verify(notifications).notifyUser(eq(admin1), eq("IN_APP"), eq("en"), any(), any());
    verify(notifications).notifyUser(eq(admin2), eq("EMAIL"), eq("bn"), any(), any());
    verify(notifications, times(4)).notifyUser(any(), any(), any(), any(), any());
    verify(audit).record(eq(doctor), eq(patient), eq("BREAK_GLASS_ACTIVATED"), any(), eq(a.getId()), anyMap());

    Instant start = now.get();
    assertThat(BreakGlassService.isActive(a, start.plus(Duration.ofMinutes(59)))).isTrue();
    assertThat(BreakGlassService.isActive(a, start.plus(Duration.ofMinutes(60)))).isFalse();
    assertThat(BreakGlassService.isActive(a, start.plus(Duration.ofMinutes(61)))).isFalse();

    // a second request while active returns the same window (no extension, no extra alerts)
    now.set(start.plus(Duration.ofMinutes(30)));
    assertThat(service.activate(patient, "Still resuscitating").getId()).isEqualTo(a.getId());
    verify(notifications, times(4)).notifyUser(any(), any(), any(), any(), any());
    assertThat(service.activeForCurrentUser(patient)).isPresent();

    // after expiry a new request opens a new window
    now.set(start.plus(Duration.ofMinutes(61)));
    assertThat(service.activeForCurrentUser(patient)).isEmpty();
    assertThat(service.activate(patient, "Second emergency episode").getId()).isNotEqualTo(a.getId());
  }

  @Test
  void adminReviewsEachAccessOnce() {
    BreakGlassAccess a = service.activate(patient, "Unconscious patient in ER");
    actor.set(admin1);
    assertThat(service.listForReview())
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.username()).isEqualTo("doctor");
              assertThat(i.patientMrn()).isEqualTo("MRN-1");
              assertThat(i.active()).isTrue();
              assertThat(i.outcome()).isNull();
            });
    service.review(a.getId(), BreakGlassReview.Outcome.JUSTIFIED, "ok");
    assertThat(a.getApprovedBy()).isEqualTo(admin1);
    assertThat(service.listForReview().get(0).outcome()).isEqualTo(BreakGlassReview.Outcome.JUSTIFIED);
    assertThatThrownBy(() -> service.review(a.getId(), BreakGlassReview.Outcome.NOT_JUSTIFIED, null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.review(UUID.randomUUID(), BreakGlassReview.Outcome.JUSTIFIED, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
