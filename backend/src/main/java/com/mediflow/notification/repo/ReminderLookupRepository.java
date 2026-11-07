package com.mediflow.notification.repo;

import com.mediflow.shared.domain.Reminder;
import com.mediflow.shared.domain.ReminderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Queries over the T00 {@code reminder} table. {@code payload.sourceId} links to Rx/appointment. */
public interface ReminderLookupRepository extends Repository<Reminder, UUID> {
  /** Due PENDING reminders, row-locked with SKIP LOCKED so parallel schedulers never double-send. */
  @Query(
      value =
          "select * from reminder where status = 'PENDING' and scheduled_at <= :now"
              + " order by scheduled_at limit :limit for update skip locked",
      nativeQuery = true)
  List<Reminder> lockDue(@Param("now") Instant now, @Param("limit") int limit);



  List<Reminder> findByPatientIdAndReminderTypeOrderByScheduledAtAsc(
      UUID patientId, String reminderType);

  List<Reminder> findByPatientIdOrderByScheduledAtAsc(UUID patientId);

  @Query(
      value =
          "select * from reminder where payload ->> 'sourceId' = :sourceId order by scheduled_at",
      nativeQuery = true)
  List<Reminder> findBySourceId(@Param("sourceId") String sourceId);

  @Modifying
  @Query(
      value =
          "update reminder set status = 'CANCELLED', updated_at = now(), version = version + 1"
              + " where payload ->> 'sourceId' = :sourceId and status = 'PENDING'",
      nativeQuery = true)
  int cancelPendingForSource(@Param("sourceId") String sourceId);

  @Query(
      "select count(r) from Reminder r where r.patientId = :patientId and r.reminderType = :type"
          + " and r.scheduledAt <= :now and r.status <> :excluded")
  long countDue(
      @Param("patientId") UUID patientId,
      @Param("type") String type,
      @Param("now") Instant now,
      @Param("excluded") ReminderStatus excluded);
}
