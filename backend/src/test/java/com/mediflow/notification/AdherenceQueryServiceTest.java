package com.mediflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediflow.notification.service.AdherenceQueryService;
import com.mediflow.notification.service.ReminderService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdherenceQueryServiceTest {

  private ReminderService reminders;
  private AdherenceQueryService port;

  @BeforeEach
  void setUp() {
    reminders = mock(ReminderService.class);
    port = new AdherenceQueryService(reminders);
  }

  @Test
  void delegatesToReminderService() {
    UUID patientId = UUID.randomUUID();
    when(reminders.adherencePercent(patientId)).thenReturn(Optional.of(75));

    assertThat(port.adherencePercent(patientId)).contains(75);
    verify(reminders).adherencePercent(patientId);
  }

  @Test
  void emptyWhenReminderServiceHasNoDueDoses() {
    UUID patientId = UUID.randomUUID();
    when(reminders.adherencePercent(patientId)).thenReturn(Optional.empty());

    assertThat(port.adherencePercent(patientId)).isEmpty();
  }

  @Test
  void nullPatientIdReturnsEmptyWithoutCallingReminders() {
    assertThat(port.adherencePercent(null)).isEmpty();
    verifyNoInteractions(reminders);
  }
}
