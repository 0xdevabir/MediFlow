package com.mediflow.identity.consent;

import com.mediflow.identity.access.AccessFacts;
import com.mediflow.identity.access.AccessPolicy;
import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.ConsentType;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Share with another doctor": time-limited referral access. The patient shares their own record,
 * or a doctor assigned to the patient shares it after the patient agreed (attested). Each share
 * writes a consent row and an {@code identity_referral_share} row; the grantee doctor is then
 * "assigned" (see {@code AccessPolicy}) until expiry or revocation.
 */
@Service
public class ReferralShareService {

  public static final Duration MIN_DURATION = Duration.ofHours(1);
  public static final Duration MAX_DURATION = Duration.ofDays(30);
  public static final Duration DEFAULT_DURATION = Duration.ofDays(7);

  private final ReferralShareRepository shares;
  private final ConsentService consents;
  private final AccessFacts facts;
  private final AccessPolicy access;
  private final CurrentUser currentUser;
  private final AuditPort audit;
  private final ClockPort clock;

  public ReferralShareService(
      ReferralShareRepository shares,
      ConsentService consents,
      AccessFacts facts,
      AccessPolicy access,
      CurrentUser currentUser,
      AuditPort audit,
      ClockPort clock) {
    this.shares = shares;
    this.consents = consents;
    this.facts = facts;
    this.access = access;
    this.currentUser = currentUser;
    this.audit = audit;
    this.clock = clock;
  }

  /** The signed-in patient's own patient id, if the user is a patient. */
  public Optional<UUID> ownPatientId() {
    return currentUser.userId().flatMap(facts::patientIdOfUser);
  }

  /** Whether the signed-in user may share / see shares of this patient. */
  public boolean canManage(UUID patientId) {
    if (patientId == null) {
      return false;
    }
    if (ownPatientId().map(patientId::equals).orElse(false)) {
      return true;
    }
    return access.isAssigned(patientId);
  }

  /**
   * @param granteeUsername username of the receiving doctor
   * @param patientAgreed must be true when a doctor (not the patient) creates the share
   * @throws AccessDeniedException caller may not share this record
   * @throws IllegalArgumentException invalid grantee or duration (message is a message key)
   */
  @Transactional
  public ReferralShare share(
      UUID patientId, String granteeUsername, Duration duration, String reason, boolean patientAgreed) {
    if (!canManage(patientId)) {
      throw new AccessDeniedException("not allowed to share this patient");
    }
    UUID actor = currentUser.requireUserId();
    boolean byPatient = ownPatientId().map(patientId::equals).orElse(false);
    if (!byPatient && !patientAgreed) {
      throw new IllegalArgumentException("identity.sharing.consentRequired");
    }
    Duration d = duration == null ? DEFAULT_DURATION : duration;
    if (d.compareTo(MIN_DURATION) < 0 || d.compareTo(MAX_DURATION) > 0) {
      throw new IllegalArgumentException("identity.sharing.duration.invalid");
    }
    UUID grantee =
        Optional.ofNullable(granteeUsername)
            .map(String::strip)
            .flatMap(facts::userIdByUsername)
            .filter(id -> facts.userHasRole(id, Role.ROLE_DOCTOR))
            .orElseThrow(() -> new IllegalArgumentException("identity.sharing.grantee.invalid"));
    if (grantee.equals(actor)) {
      throw new IllegalArgumentException("identity.sharing.grantee.self");
    }
    Instant now = clock.now();
    Consent consent =
        consents.recordConsent(
            patientId,
            byPatient ? actor : null,
            ConsentType.DATA_PROCESSING,
            true,
            ConsentService.REFERRAL_SHARE_VERSION);
    ReferralShare s = new ReferralShare();
    s.setPatientId(patientId);
    s.setGranteeUserId(grantee);
    s.setGrantedBy(actor);
    s.setConsentId(consent.getId());
    s.setReason(reason == null || reason.isBlank() ? null : reason.strip());
    s.setStartsAt(now);
    s.setExpiresAt(now.plus(d));
    ReferralShare saved = shares.save(s);
    audit.record(
        actor,
        patientId,
        "REFERRAL_SHARE_GRANTED",
        "ReferralShare",
        saved.getId(),
        Map.of("grantee", grantee.toString(), "expiresAt", saved.getExpiresAt().toString()));
    return saved;
  }

  /** Revoke: the patient, the doctor who shared it, or an admin. */
  @Transactional
  public void revoke(UUID shareId) {
    ReferralShare s =
        shares.findById(shareId).orElseThrow(() -> new IllegalArgumentException("identity.sharing.unknown"));
    UUID actor = currentUser.requireUserId();
    boolean allowed =
        actor.equals(s.getGrantedBy())
            || ownPatientId().map(s.getPatientId()::equals).orElse(false)
            || currentUser.hasRole("ROLE_ADMIN");
    if (!allowed) {
      throw new AccessDeniedException("not allowed to revoke this share");
    }
    if (s.getRevokedAt() != null) {
      return;
    }
    s.setRevokedAt(clock.now());
    shares.save(s);
    consents.revoke(s.getConsentId());
    audit.record(actor, s.getPatientId(), "REFERRAL_SHARE_REVOKED", "ReferralShare", s.getId(), Map.of());
  }

  @Transactional(readOnly = true)
  public List<ReferralShare> listForPatient(UUID patientId) {
    if (!canManage(patientId)) {
      throw new AccessDeniedException("not allowed");
    }
    return shares.findByPatientIdOrderByStartsAtDesc(patientId);
  }
}
