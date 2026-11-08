package com.mediflow.notification.service;

import com.mediflow.shared.port.AdherenceQueryPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V2-10 adapter: exposes reminder adherence % to other modules via {@link AdherenceQueryPort}
 * (patient home tile, V2-03 dashboard) without crossing package boundaries into ReminderService.
 */
@Service
public class AdherenceQueryService implements AdherenceQueryPort {

  private final ReminderService reminders;

  public AdherenceQueryService(ReminderService reminders) {
    this.reminders = reminders;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Integer> adherencePercent(UUID patientId) {
    if (patientId == null) {
      return Optional.empty();
    }
    return reminders.adherencePercent(patientId);
  }
}
