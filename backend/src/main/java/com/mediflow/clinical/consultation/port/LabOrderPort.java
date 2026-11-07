package com.mediflow.clinical.consultation.port;

import java.util.UUID;

/**
 * Minimal T07 seam. Until Lab module registers a bean, {@link NoOpLabOrderPort} publishes a local
 * {@link com.mediflow.shared.event.DomainEvents.LabOrderRequested} event only (no order row is created).
 */
public interface LabOrderPort {

  /**
   * Request a doctor-ordered lab test for the encounter's patient.
   *
   * @return lab order id when T07 is present; empty UUID when only the event was published
   */
  UUID requestOrder(UUID encounterId, UUID patientId, UUID hospitalId, String testCode);
}
