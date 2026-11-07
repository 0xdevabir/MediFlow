package com.mediflow.shared.domain;

public enum ConsentType {
  TERMS,
  DATA_PROCESSING,
  RESEARCH,
  FHIR_SHARE,
  /** Explicit patient consent to share the record with a referred doctor (T01). */
  REFERRAL_SHARE,
}
