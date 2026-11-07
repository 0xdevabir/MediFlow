package com.mediflow.appointment.web;

import com.mediflow.appointment.queue.QueueBoardRow;
import com.mediflow.appointment.queue.QueueMeta;
import com.mediflow.appointment.queue.QueueMetaRepository;
import com.mediflow.appointment.queue.QueueService;
import com.mediflow.appointment.queue.QueueSseHub;
import com.mediflow.appointment.support.QueueClock;
import com.mediflow.shared.port.ClockPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/queue")
public class QueueApiController {

  private final QueueService queues;
  private final QueueMetaRepository metas;
  private final QueueSseHub sseHub;
  private final ClockPort clock;

  public QueueApiController(
      QueueService queues, QueueMetaRepository metas, QueueSseHub sseHub, ClockPort clock) {
    this.queues = queues;
    this.metas = metas;
    this.sseHub = sseHub;
    this.clock = clock;
  }

  @GetMapping("/board")
  @PreAuthorize("hasAnyRole('FRONT_DESK','NURSE','DOCTOR','ADMIN')")
  public List<QueueBoardRow> board(
      @RequestParam UUID doctorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    LocalDate day = date != null ? date : QueueClock.queueDate(clock.now());
    return queues.board(doctorId, day);
  }

  @GetMapping("/appointments/{appointmentId}/status")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Object> status(@PathVariable UUID appointmentId) {
    Optional<QueueBoardRow> row = queues.statusForAppointment(appointmentId);
    Optional<QueueMeta> meta = metas.findByAppointmentId(appointmentId);
    if (row.isEmpty()) {
      return Map.of("present", false);
    }
    QueueBoardRow r = row.get();
    return Map.of(
        "present",
        true,
        "tokenNo",
        r.tokenNo(),
        "ahead",
        r.ahead(),
        "position",
        r.position(),
        "status",
        r.status().name(),
        "level",
        r.level().name(),
        "doctorId",
        meta.map(m -> m.getDoctorId().toString()).orElse(""),
        "queueDate",
        meta.map(m -> m.getQueueDate().toString()).orElse(""));
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @PreAuthorize("isAuthenticated()")
  public SseEmitter stream(
      @RequestParam UUID doctorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    LocalDate day = date != null ? date : QueueClock.queueDate(clock.now());
    return sseHub.subscribe(doctorId, day);
  }
}
