package com.mediflow.prescription.safety;

import com.mediflow.prescription.domain.CheckType;
import com.mediflow.shared.domain.ConflictSeverity;
import java.util.Objects;
import java.util.UUID;

/** One contradiction-engine finding for a draft item (or draft-wide when itemId is null). */
public record SafetyFinding(
    UUID itemId,
    CheckType checkType,
    ConflictSeverity severity,
    String fingerprint,
    String messageKey,
    String detail,
    boolean overridable) {

  public SafetyFinding {
    Objects.requireNonNull(checkType);
    Objects.requireNonNull(severity);
    Objects.requireNonNull(fingerprint);
    Objects.requireNonNull(messageKey);
  }

  public static SafetyFinding of(
      UUID itemId,
      CheckType checkType,
      ConflictSeverity severity,
      String fingerprint,
      String messageKey,
      String detail,
      boolean overridable) {
    return new SafetyFinding(
        itemId, checkType, severity, fingerprint, messageKey, detail, overridable);
  }
}
