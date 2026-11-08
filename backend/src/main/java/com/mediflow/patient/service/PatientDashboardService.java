package com.mediflow.patient.service;

import com.mediflow.shared.port.AdherenceQueryPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientDashboardService {

  private final PatientAccessGuard access;
  private final PatientProfileService profiles;
  private final ObjectProvider<AdherenceQueryPort> adherence;
  private final PatientAppointmentGlancePort appointments;
  private final QrTokenService qrTokens;

  public PatientDashboardService(
      PatientAccessGuard access,
      PatientProfileService profiles,
      ObjectProvider<AdherenceQueryPort> adherence,
      PatientAppointmentGlancePort appointments,
      QrTokenService qrTokens) {
    this.access = access;
    this.profiles = profiles;
    this.adherence = adherence;
    this.appointments = appointments;
    this.qrTokens = qrTokens;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@patientAccess.isSelf(#patientId)")
  public DashboardView load(UUID patientId) {
    PatientView patient = profiles.get(patientId);
    AdherenceQueryPort port = adherence.getIfAvailable();
    Optional<Integer> pct =
        port == null ? Optional.empty() : port.adherencePercent(patientId);
    QrTokenService.IssuedToken token = profiles.issueCardToken(patientId);
    return new DashboardView(
        patient,
        pct.orElse(null),
        port != null,
        appointments.nextUpcoming(patientId).orElse(null),
        qrTokens.qrDataUri(token.token()),
        token.expiresAt());
  }

  public record DashboardView(
      PatientView patient,
      Integer pct,
      boolean adherenceConfigured,
      PatientAppointmentGlancePort.NextVisit nextVisit,
      String qrDataUri,
      java.time.Instant qrExpiresAt) {}
}
