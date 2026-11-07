package com.mediflow.admin.settings;

/** Keys that require dual ADMIN + CLINICAL_APPROVER approval before they take effect. */
public final class GovernedKeys {

  public static final String AI_CONFIDENCE_THRESHOLD = "ai.confidence.threshold";

  private GovernedKeys() {}

  public static boolean requiresDualApproval(String key) {
    if (key == null) {
      return false;
    }
    return AI_CONFIDENCE_THRESHOLD.equals(key)
        || key.startsWith("clinical.")
        || key.startsWith("vital_rule.")
        || key.startsWith("drug_interaction.");
  }

  /** Raise-only: a lower value than the live setting must be rejected. */
  public static boolean isRaiseOnly(String key) {
    return AI_CONFIDENCE_THRESHOLD.equals(key);
  }
}
