package com.mediflow.patient.service;

import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.PatientQrPort;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared {@link PatientQrPort} for pharmacy / lab identity scans. Tokens use the same HMAC format
 * as the patient card ({@link QrTokenService}); TTL comes from the caller or {@code
 * mediflow.patient.qr-token-ttl}.
 */
@Component
public class PatientQrPortAdapter implements PatientQrPort {

  private final QrTokenService qrTokens;
  private final PatientLookupRepository patients;

  public PatientQrPortAdapter(QrTokenService qrTokens, PatientLookupRepository patients) {
    this.qrTokens = qrTokens;
    this.patients = patients;
  }

  @Override
  @Transactional(readOnly = true)
  public String issueToken(UUID patientId, Duration ttl) {
    if (patientId == null) {
      throw new IllegalArgumentException("patientId is required");
    }
    Patient patient =
        patients
            .findById(patientId)
            .orElseThrow(() -> new PatientRuleException("patient.notFound"));
    Duration effective = ttl != null ? ttl : qrTokens.defaultTtl();
    return qrTokens.issue(patient.getId(), patient.getMrn(), effective).token();
  }

  @Override
  public Optional<UUID> resolvePatientId(String token) {
    return qrTokens.verify(token).map(QrTokenService.QrIdentity::patientId);
  }
}
