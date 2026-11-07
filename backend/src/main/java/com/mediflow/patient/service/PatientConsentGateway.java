package com.mediflow.patient.service;

import java.util.UUID;

/**
 * Minimal consent capability T02 needs from T01 ({@code identity.consent.ConsentService}). Defined
 * here so this module does not inject another module's service; the default implementation writes
 * the shared {@code consent} table, which is the same table T01's ConsentService reads. See
 * docs/plan/requests/T02.md.
 */
public interface PatientConsentGateway {

  String REGISTRATION_VERSION = "REGISTRATION/v1";

  /** Records TERMS + DATA_PROCESSING consent given at registration. */
  void recordRegistrationConsent(UUID patientId, UUID userId, String versionLabel);

  boolean hasRegistrationConsent(UUID patientId);
}
