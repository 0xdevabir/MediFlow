package com.mediflow.identity.breakglass;

import com.mediflow.shared.domain.BreakGlassAccess;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emergency "break the glass" access (R&D §12): a doctor or ER user states a reason and gets
 * doctor-level access to one patient for 60 minutes. Admins are notified at once (in-app + e-mail)
 * and review every use afterwards. Read by {@code AccessPolicy} / {@code PatientAccessPort} through
 * the {@code break_glass_access} row's validity window.
 */
@Service
public class BreakGlassService {

  public static final Duration DURATION = Duration.ofMinutes(60);
  public static final int MIN_REASON_LENGTH = 10;
  public static final int MAX_REASON_LENGTH = 1000;

  /** One row of the admin review list. */
  public record ReviewItem(
      UUID id,
      String username,
      String patientMrn,
      String reason,
      Instant startedAt,
      Instant expiresAt,
      boolean active,
      BreakGlassReview.Outcome outcome,
      String reviewNote) {

    public java.time.LocalDateTime startedLocal() {
      return java.time.LocalDateTime.ofInstant(startedAt, com.mediflow.identity.access.AccessRules.CLINIC_ZONE);
    }

    public java.time.LocalDateTime expiresLocal() {
      return java.time.LocalDateTime.ofInstant(expiresAt, com.mediflow.identity.access.AccessRules.CLINIC_ZONE);
    }
  }

  private final IdentityBreakGlassRepository accesses;
  private final BreakGlassReviewRepository reviews;
  private final IdentityAdminDirectory directory;
  private final NotificationPort notifications;
  private final AuditPort audit;
  private final ClockPort clock;
  private final CurrentUser currentUser;
  private final MessageSource messages;

  public BreakGlassService(
      IdentityBreakGlassRepository accesses,
      BreakGlassReviewRepository reviews,
      IdentityAdminDirectory directory,
      NotificationPort notifications,
      AuditPort audit,
      ClockPort clock,
      CurrentUser currentUser,
      MessageSource messages) {
    this.accesses = accesses;
    this.reviews = reviews;
    this.directory = directory;
    this.notifications = notifications;
    this.audit = audit;
    this.clock = clock;
    this.currentUser = currentUser;
    this.messages = messages;
  }

  public static boolean isActive(BreakGlassAccess row, Instant now) {
    return row != null && !row.getStartedAt().isAfter(now) && now.isBefore(row.getExpiresAt());
  }

  /**
   * Opens 60-minute access for the signed-in doctor / ER user. An already-active window for the same
   * patient is returned unchanged (not extended).
   *
   * @throws IllegalArgumentException reason too short/long or unknown patient
   */
  @PreAuthorize("hasAnyRole('DOCTOR','ER')")
  @Transactional
  public BreakGlassAccess activate(UUID patientId, String reason) {
    String trimmed = reason == null ? "" : reason.strip();
    if (trimmed.length() < MIN_REASON_LENGTH || trimmed.length() > MAX_REASON_LENGTH) {
      throw new IllegalArgumentException("identity.breakglass.reason.invalid");
    }
    if (patientId == null || !directory.patientExists(patientId)) {
      throw new IllegalArgumentException("identity.breakglass.patient.unknown");
    }
    UUID actor = currentUser.requireUserId();
    Instant now = clock.now();
    List<BreakGlassAccess> active = accesses.findActive(actor, patientId, now);
    if (!active.isEmpty()) {
      return active.get(0);
    }
    BreakGlassAccess row = new BreakGlassAccess();
    row.setUserId(actor);
    row.setPatientId(patientId);
    row.setReason(trimmed);
    row.setStartedAt(now);
    row.setExpiresAt(now.plus(DURATION));
    BreakGlassAccess saved = accesses.save(row);
    audit.record(
        actor,
        patientId,
        "BREAK_GLASS_ACTIVATED",
        "BreakGlassAccess",
        saved.getId(),
        Map.of("reason", trimmed, "expiresAt", saved.getExpiresAt().toString()));
    notifyAdmins(saved);
    return saved;
  }

  /** Active break-glass of the signed-in user for the patient, if any. */
  @Transactional(readOnly = true)
  public Optional<BreakGlassAccess> activeForCurrentUser(UUID patientId) {
    return currentUser
        .userId()
        .flatMap(uid -> accesses.findActive(uid, patientId, clock.now()).stream().findFirst());
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<ReviewItem> listForReview() {
    List<BreakGlassAccess> rows = accesses.findTop200ByOrderByStartedAtDesc();
    Map<UUID, String> names = directory.usernames(rows.stream().map(BreakGlassAccess::getUserId).distinct().toList());
    Map<UUID, String> mrns = directory.mrns(rows.stream().map(BreakGlassAccess::getPatientId).distinct().toList());
    Instant now = clock.now();
    return rows.stream()
        .map(
            r -> {
              Optional<BreakGlassReview> review = reviews.findByBreakGlassId(r.getId());
              return new ReviewItem(
                  r.getId(),
                  names.getOrDefault(r.getUserId(), "?"),
                  mrns.getOrDefault(r.getPatientId(), "?"),
                  r.getReason(),
                  r.getStartedAt(),
                  r.getExpiresAt(),
                  isActive(r, now),
                  review.map(BreakGlassReview::getOutcome).orElse(null),
                  review.map(BreakGlassReview::getNote).orElse(null));
            })
        .toList();
  }

  /** Admin records the review outcome; one review per access. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public BreakGlassReview review(UUID breakGlassId, BreakGlassReview.Outcome outcome, String note) {
    if (outcome == null) {
      throw new IllegalArgumentException("identity.breakglass.review.outcome.missing");
    }
    BreakGlassAccess access =
        accesses
            .findById(breakGlassId)
            .orElseThrow(() -> new IllegalArgumentException("identity.breakglass.unknown"));
    if (reviews.findByBreakGlassId(breakGlassId).isPresent()) {
      throw new IllegalStateException("identity.breakglass.review.done");
    }
    UUID actor = currentUser.requireUserId();
    BreakGlassReview review = new BreakGlassReview();
    review.setBreakGlassId(breakGlassId);
    review.setReviewedBy(actor);
    review.setOutcome(outcome);
    review.setNote(note == null || note.isBlank() ? null : note.strip());
    review.setReviewedAt(clock.now());
    BreakGlassReview saved = reviews.save(review);
    access.setApprovedBy(outcome == BreakGlassReview.Outcome.JUSTIFIED ? actor : null);
    accesses.save(access);
    audit.record(
        actor,
        access.getPatientId(),
        "BREAK_GLASS_REVIEWED",
        "BreakGlassAccess",
        breakGlassId,
        Map.of("outcome", outcome.name()));
    return saved;
  }

  private void notifyAdmins(BreakGlassAccess row) {
    String who = currentUser.username().orElse("?");
    String mrn = directory.mrns(List.of(row.getPatientId())).getOrDefault(row.getPatientId(), "?");
    for (IdentityAdminDirectory.Recipient admin : directory.enabledUsersWithRole(Role.ROLE_ADMIN)) {
      Locale locale = Locale.forLanguageTag(admin.locale() == null ? "en" : admin.locale());
      String subject =
          messages.getMessage("identity.breakglass.notify.subject", null, "Break-glass access used", locale);
      String body =
          messages.getMessage(
              "identity.breakglass.notify.body",
              new Object[] {who, mrn, row.getReason(), row.getExpiresAt().toString()},
              "Break-glass: " + who + " opened patient " + mrn + ". Reason: " + row.getReason(),
              locale);
      notifications.notifyUser(admin.userId(), "IN_APP", locale.toLanguageTag(), subject, body);
      notifications.notifyUser(admin.userId(), "EMAIL", locale.toLanguageTag(), subject, body);
    }
  }
}
