package com.mediflow.identity.consent;

import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.ConsentType;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consent records (R&D §12, Bangladesh DPA 2023 alignment). T02 calls {@link #recordConsent} at
 * patient registration (terms + data processing, optional research). Every change is audited; a
 * revoked or refused consent is never "active".
 */
@Service
public class ConsentService {

  /**
   * Version label used for the consent row behind a referral share. Prefer {@link
   * ConsentType#REFERRAL_SHARE}; the DATA_PROCESSING + this label form is kept for rows written
   * before T14.
   */
  public static final String REFERRAL_SHARE_VERSION = "REFERRAL_SHARE/v1";

  private final IdentityConsentRepository consents;
  private final AuditPort audit;
  private final ClockPort clock;
  private final CurrentUser currentUser;

  public ConsentService(
      IdentityConsentRepository consents, AuditPort audit, ClockPort clock, CurrentUser currentUser) {
    this.consents = consents;
    this.audit = audit;
    this.clock = clock;
    this.currentUser = currentUser;
  }

  /**
   * Records a consent decision.
   *
   * @param patientId patient the consent is about (required)
   * @param userId the patient's {@code app_user} id, or null if the patient has no login yet
   * @param granted {@code false} records an explicit refusal
   * @param versionLabel the text version shown, e.g. {@code "TERMS/2026-11"}
   */
  @Transactional
  public Consent recordConsent(
      UUID patientId, UUID userId, ConsentType type, boolean granted, String versionLabel) {
    if (patientId == null || type == null || versionLabel == null || versionLabel.isBlank()) {
      throw new IllegalArgumentException("patientId, type and versionLabel are required");
    }
    Consent c = new Consent();
    c.setPatientId(patientId);
    c.setUserId(userId);
    c.setConsentType(type);
    c.setGranted(granted);
    c.setVersionLabel(versionLabel.strip());
    c.setGrantedAt(clock.now());
    Consent saved = consents.save(c);
    audit.record(
        currentUser.userId().orElse(userId),
        patientId,
        granted ? "CONSENT_GRANTED" : "CONSENT_REFUSED",
        "Consent",
        saved.getId(),
        Map.of("type", type.name(), "version", saved.getVersionLabel()));
    return saved;
  }

  /** Latest decision of this type is a grant that has not been revoked. */
  @Transactional(readOnly = true)
  public boolean hasActiveConsent(UUID patientId, ConsentType type) {
    if (patientId == null || type == null) {
      return false;
    }
    // Exclude legacy DATA_PROCESSING rows that actually represent referral shares.
    if (type == ConsentType.DATA_PROCESSING) {
      return consents.findByPatientIdAndConsentTypeOrderByGrantedAtDesc(patientId, type).stream()
          .filter(c -> !REFERRAL_SHARE_VERSION.equals(c.getVersionLabel()))
          .findFirst()
          .map(c -> c.getGranted() && c.getRevokedAt() == null)
          .orElse(false);
    }
    return consents.findByPatientIdAndConsentTypeOrderByGrantedAtDesc(patientId, type).stream()
        .findFirst()
        .map(c -> c.getGranted() && c.getRevokedAt() == null)
        .orElse(false);
  }

  @Transactional
  public void revoke(UUID consentId) {
    Consent c =
        consents.findById(consentId).orElseThrow(() -> new IllegalArgumentException("unknown consent"));
    if (c.getRevokedAt() != null) {
      return;
    }
    c.setRevokedAt(clock.now());
    consents.save(c);
    audit.record(
        currentUser.userId().orElse(null),
        c.getPatientId(),
        "CONSENT_REVOKED",
        "Consent",
        c.getId(),
        Map.of("type", c.getConsentType().name()));
  }

  @Transactional(readOnly = true)
  public List<Consent> listForPatient(UUID patientId) {
    return consents.findByPatientIdOrderByGrantedAtDesc(patientId);
  }
}
