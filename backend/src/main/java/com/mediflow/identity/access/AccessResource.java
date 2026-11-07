package com.mediflow.identity.access;

/** Rows of the access matrix (R&D documentation v1.3 §12, Technical documentation Table 17). */
public enum AccessResource {
  DEMOGRAPHICS(true),
  HISTORY_ALLERGIES(true),
  VITALS(true),
  APPOINTMENTS(true),
  DIRECTORY(false),
  CONSULTATION_NOTES(true),
  DRAFT_PRESCRIPTION(true),
  SIGNED_PRESCRIPTION(true),
  LAB_ORDERS(true),
  LAB_REPORTS(true),
  DISPENSING(true),
  AI_FLAGS(true),
  AUDIT_LOG(true),
  SETTINGS_USERS(false);

  private final boolean patientScoped;

  AccessResource(boolean patientScoped) {
    this.patientScoped = patientScoped;
  }

  /** Whether a cell for this resource is bound to one patient's record. */
  public boolean patientScoped() {
    return patientScoped;
  }
}
