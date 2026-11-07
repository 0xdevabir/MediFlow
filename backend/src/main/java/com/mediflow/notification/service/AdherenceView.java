package com.mediflow.notification.service;

import com.mediflow.notification.repo.RecipientLookupRepository;
import com.mediflow.shared.web.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Template helper (bean {@code notificationAdherence}) for the adherence widget that T02's patient
 * dashboard includes via {@code notification/fragments :: adherence}.
 */
@Service("notificationAdherence")
public class AdherenceView {

  private final ReminderService reminders;
  private final RecipientLookupRepository lookup;
  private final CurrentUser currentUser;

  public AdherenceView(
      ReminderService reminders, RecipientLookupRepository lookup, CurrentUser currentUser) {
    this.reminders = reminders;
    this.lookup = lookup;
    this.currentUser = currentUser;
  }

  /** Patient record of the signed-in user, if they are a patient. */
  @Transactional(readOnly = true)
  public Optional<UUID> currentPatientId() {
    return currentUser.userId().flatMap(u -> lookup.findPatientIdByUserId(u).stream().findFirst());
  }

  /** Adherence % for the signed-in patient, or null when not a patient / nothing due yet. */
  @Transactional(readOnly = true)
  public Integer currentPercent() {
    return currentPatientId().flatMap(reminders::adherencePercent).orElse(null);
  }

  /** Adherence % for a given patient (for staff views), or null when nothing is due. */
  @Transactional(readOnly = true)
  public Integer percentFor(UUID patientId) {
    return patientId == null ? null : reminders.adherencePercent(patientId).orElse(null);
  }
}
