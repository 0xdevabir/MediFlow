package com.mediflow.lab.service;

import com.mediflow.shared.port.LabOrderPort;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Real {@link LabOrderPort}: creates a doctor-ordered {@code lab_order} row via {@link
 * LabOrderService}. Replaces the shared {@code @ConditionalOnMissingBean} NoOp.
 */
@Component
@Primary
public class LabModuleLabOrderPort implements LabOrderPort {

  private final LabOrderService orders;

  public LabModuleLabOrderPort(LabOrderService orders) {
    this.orders = orders;
  }

  @Override
  public UUID requestOrder(UUID encounterId, UUID patientId, UUID hospitalId, String testCode) {
    return orders.requestOrderByTestCode(encounterId, patientId, hospitalId, testCode);
  }
}
