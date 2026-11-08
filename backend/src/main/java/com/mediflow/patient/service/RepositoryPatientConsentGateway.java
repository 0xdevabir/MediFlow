package com.mediflow.patient.service;

import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.ConsentType;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.ConsentRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical registration consent writer for the shared {@code consent} table.
 * Marked {@code @Primary} so any future identity adapter does not double-bind
 * {@link PatientConsentGateway} at registration.
 */
@Component
@Primary
public class RepositoryPatientConsentGateway implements PatientConsentGateway {

  private final ConsentRepository consents;
  private final ClockPort clock;
  private final JdbcTemplate jdbc;

  public RepositoryPatientConsentGateway(
      ConsentRepository consents, ClockPort clock, JdbcTemplate jdbc) {
    this.consents = consents;
    this.clock = clock;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void recordRegistrationConsent(UUID patientId, UUID userId, String versionLabel) {
    Instant now = clock.now();
    for (ConsentType type : new ConsentType[] {ConsentType.TERMS, ConsentType.DATA_PROCESSING}) {
      Consent c = new Consent();
      c.setPatientId(patientId);
      c.setUserId(userId);
      c.setConsentType(type);
      c.setGranted(true);
      c.setVersionLabel(versionLabel);
      c.setGrantedAt(now);
      consents.save(c);
    }
    consents.flush();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasRegistrationConsent(UUID patientId) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM consent WHERE patient_id = ? AND consent_type = ?"
                + " AND granted AND revoked_at IS NULL)",
            Boolean.class,
            patientId,
            ConsentType.DATA_PROCESSING.name());
    return Boolean.TRUE.equals(exists);
  }
}
