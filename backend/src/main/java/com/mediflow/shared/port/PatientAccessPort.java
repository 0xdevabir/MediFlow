package com.mediflow.shared.port;

import java.util.UUID;

/**
 * Deny-by-default patient record access checks.
 *
 * <p><b>Implementor:</b> V2-02 identity (assigned-doctor + ER assignee + break-glass). Stub denies
 * all until that bean is present.
 */
public interface PatientAccessPort {

  /** Doctors see assigned patients only (deny by default). */
  boolean isAssigned(UUID doctorId, UUID patientId);

  /**
   * ER staff may read a patient when there is an <em>active</em> red alert with {@code
   * assigned_er_user_id = erUserId}, or via a valid break-glass grant. Deny by default.
   *
   * <p>Implemented by V2-02. Default denies so older implementors keep compiling.
   */
  default boolean isErAssigned(UUID erUserId, UUID patientId) {
    return false;
  }
}

