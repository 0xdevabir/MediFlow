package com.mediflow.prescription.domain;

/** Contradiction checks run on every draft (PDF §11 contradiction table, DOCX Table 13). */
public enum CheckType {
  ALLERGY,
  INTERACTION,
  DOSE_RANGE,
  DOSE_UNVERIFIED,
  DUPLICATE_THERAPY,
  CONDITION,
  PREGNANCY,
  VITALS,
  UNKNOWN_ITEM
}
