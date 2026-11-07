package com.mediflow.clinical.consultation.port;

import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/** Publishes {@link LabOrderRequested} only. T07 should replace this bean with a real writer. */
public class NoOpLabOrderPort implements LabOrderPort {

  private static final Logger log = LoggerFactory.getLogger(NoOpLabOrderPort.class);

  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;
  private final ClockPort clock;

  public NoOpLabOrderPort(
      ApplicationEventPublisher events, CurrentUser currentUser, ClockPort clock) {
    this.events = events;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Override
  public UUID requestOrder(UUID encounterId, UUID patientId, UUID hospitalId, String testCode) {
    UUID actor = currentUser.requireUserId();
    events.publishEvent(
        new LabOrderRequested(encounterId, patientId, hospitalId, testCode, actor, clock.now()));
    log.info(
        "LabOrderRequested (no-op port) encounter={} patient={} test={}",
        encounterId,
        patientId,
        testCode);
    return new UUID(0L, 0L);
  }
}
