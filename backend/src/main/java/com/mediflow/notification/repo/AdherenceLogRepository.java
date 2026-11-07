package com.mediflow.notification.repo;

import com.mediflow.notification.domain.AdherenceLog;
import com.mediflow.notification.domain.AdherenceOutcome;
import com.mediflow.shared.domain.ReminderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdherenceLogRepository extends JpaRepository<AdherenceLog, UUID> {

  Optional<AdherenceLog> findByReminderId(UUID reminderId);

  List<AdherenceLog> findByReminderIdIn(List<UUID> reminderIds);

  /** Acknowledgements with {@code outcome} for reminders already due and not cancelled. */
  @Query(
      "select count(a) from AdherenceLog a, Reminder r where r.id = a.reminderId"
          + " and a.patientId = :patientId and a.outcome = :outcome and r.scheduledAt <= :now"
          + " and r.status <> :excluded")
  long countForDue(
      @Param("patientId") UUID patientId,
      @Param("outcome") AdherenceOutcome outcome,
      @Param("now") Instant now,
      @Param("excluded") ReminderStatus excluded);
}
