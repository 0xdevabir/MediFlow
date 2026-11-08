package com.mediflow.shared.port;

import java.util.UUID;

/**
 * Cross-module façade for creating doctor-ordered lab orders from consultation.
 *
 * <p><b>Implementor:</b> V2-08 ({@code com.mediflow.lab}). Until then, the
 * {@code @ConditionalOnMissingBean} NoOp under {@code shared.port.stub} logs and publishes {@code
 * DomainEvents.LabOrderRequested}.
 *
 * <p><b>Consumers:</b> V2-06 clinical consultation.
 */
public interface LabOrderPort {

  /**
   * Creates a real doctor-ordered {@code lab_order} (or throws). Returns the lab order id.
   *
   * <p>The temporary NoOp returns the nil UUID {@code 00000000-0000-0000-0000-000000000000} after
   * publishing {@code LabOrderRequested}.
   */
  UUID requestOrder(UUID encounterId, UUID patientId, UUID hospitalId, String testCode);
}
