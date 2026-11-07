package com.mediflow.clinical.consultation.port;

import java.util.UUID;

/**
 * Object-level clinical access. T01's {@code AccessPolicy} is the real implementation when present;
 * {@link DefaultClinicalAccess} is the safe deny-by-default fallback used on this branch.
 */
public interface ClinicalAccess {

  boolean canReadPatient(UUID patientId);

  boolean canWriteVitals(UUID patientId);

  /** Assigned doctor (or break-glass / referral) may open / edit the consultation. */
  boolean canConsult(UUID patientId);

  boolean canOrderLab(UUID patientId);
}
