package com.mediflow.appointment.support;

import com.mediflow.shared.port.ClockPort;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Today's queue / Red Alert counts for role home tiles (Thymeleaf: {@code @appointmentHomeStats}).
 */
@Component("appointmentHomeStats")
public class AppointmentHomeStats {

  private final NamedParameterJdbcTemplate jdbc;
  private final ClockPort clock;

  public AppointmentHomeStats(NamedParameterJdbcTemplate jdbc, ClockPort clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public long waitingToday() {
    LocalDate day = QueueClock.queueDate(clock.now());
    Long n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM appointment_queue_meta m
            JOIN queue_entry e ON e.id = m.queue_entry_id
            WHERE m.queue_date = :day
              AND m.removed_reason IS NULL
              AND e.status = 'WAITING'
            """,
            new MapSqlParameterSource("day", day),
            Long.class);
    return n == null ? 0L : n;
  }

  public long waitingForDoctor(UUID doctorId) {
    if (doctorId == null) {
      return 0L;
    }
    LocalDate day = QueueClock.queueDate(clock.now());
    Long n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM appointment_queue_meta m
            JOIN queue_entry e ON e.id = m.queue_entry_id
            WHERE m.queue_date = :day
              AND m.doctor_id = :doctorId
              AND m.removed_reason IS NULL
              AND e.status = 'WAITING'
            """,
            new MapSqlParameterSource()
                .addValue("day", day)
                .addValue("doctorId", doctorId),
            Long.class);
    return n == null ? 0L : n;
  }

  public long activeRedAlerts() {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM red_alert WHERE handover_status IS NULL",
            new MapSqlParameterSource(),
            Long.class);
    return n == null ? 0L : n;
  }

  public long emergencyFlagsToday() {
    LocalDate day = QueueClock.queueDate(clock.now());
    Long n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM appointment_booking_meta bm
            JOIN appointment a ON a.id = bm.appointment_id
            WHERE bm.emergency_flag = TRUE
              AND a.status IN ('BOOKED','CHECKED_IN','IN_PROGRESS')
              AND CAST(COALESCE(bm.emergency_flagged_at, a.booked_at) AS DATE) = :day
            """,
            new MapSqlParameterSource("day", day),
            Long.class);
    return n == null ? 0L : n;
  }
}
