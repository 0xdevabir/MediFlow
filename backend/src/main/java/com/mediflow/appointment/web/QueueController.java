package com.mediflow.appointment.web;

import com.mediflow.appointment.queue.QueueBoardRow;
import com.mediflow.appointment.queue.QueueService;
import com.mediflow.appointment.support.AppointmentAccess;
import com.mediflow.appointment.support.QueueClock;
import com.mediflow.appointment.triage.TriageService;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/queue")
public class QueueController {

  private final QueueService queues;
  private final TriageService triage;
  private final AppointmentAccess access;
  private final CurrentUser currentUser;
  private final ClockPort clock;

  public QueueController(
      QueueService queues,
      TriageService triage,
      AppointmentAccess access,
      CurrentUser currentUser,
      ClockPort clock) {
    this.queues = queues;
    this.triage = triage;
    this.access = access;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /** Prefer `/queue/home` — exact `/queue` is still claimed by T00's placeholder. */
  @GetMapping("/home")
  public String index(
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Model model) {
    if (currentUser.hasRole("PATIENT")) {
      return "redirect:/queue/me";
    }
    UUID effectiveDoctor = doctorId;
    if (effectiveDoctor == null && currentUser.hasRole("DOCTOR")) {
      effectiveDoctor = access.requireCurrentDoctorId();
    }
    LocalDate day = date != null ? date : QueueClock.queueDate(clock.now());
    List<QueueBoardRow> board =
        effectiveDoctor == null ? List.of() : queues.board(effectiveDoctor, day);
    model.addAttribute("board", board);
    model.addAttribute("doctorId", effectiveDoctor);
    model.addAttribute("queueDate", day);
    model.addAttribute("levels", TriageLevel.values());
    return "queue/board";
  }

  @GetMapping("/me")
  @PreAuthorize("hasRole('PATIENT')")
  public String myStatus(Model model) {
    Optional<QueueBoardRow> status = queues.myLiveStatus();
    model.addAttribute("status", status.orElse(null));
    status.ifPresent(
        s -> {
          model.addAttribute("doctorId", null);
          // doctor id is needed for SSE — resolve via appointment status page API
        });
    return "queue/patient";
  }

  @GetMapping("/doctor")
  @PreAuthorize("hasRole('DOCTOR')")
  public String doctorBoard(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Model model) {
    UUID doctorId = access.requireCurrentDoctorId();
    LocalDate day = date != null ? date : QueueClock.queueDate(clock.now());
    model.addAttribute("board", queues.board(doctorId, day));
    model.addAttribute("doctorId", doctorId);
    model.addAttribute("queueDate", day);
    return "queue/doctor";
  }

  @PostMapping("/call-next")
  @PreAuthorize("hasRole('DOCTOR')")
  public String callNext(
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      RedirectAttributes ra) {
    Optional<QueueBoardRow> called = queues.callNext(doctorId, date);
    if (called.isEmpty()) {
      ra.addFlashAttribute("errorKey", "appointment.error.queueEmpty");
    } else {
      ra.addFlashAttribute("successKey", "appointment.flash.called");
      ra.addFlashAttribute("tokenNo", called.get().tokenNo());
    }
    return "redirect:/queue/doctor";
  }

  @PostMapping("/{entryId}/move-up")
  @PreAuthorize("hasRole('NURSE')")
  public String moveUp(
      @PathVariable UUID entryId,
      @RequestParam String reason,
      @RequestParam UUID doctorId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      RedirectAttributes ra) {
    queues.moveUp(entryId, reason);
    ra.addFlashAttribute("successKey", "appointment.flash.queueOverride");
    return "redirect:/queue/home?doctorId=" + doctorId + "&date=" + date;
  }

  @PostMapping("/{entryId}/reorder")
  @PreAuthorize("hasRole('NURSE')")
  public String reorder(
      @PathVariable UUID entryId,
      @RequestParam UUID targetEntryId,
      @RequestParam(defaultValue = "false") boolean after,
      @RequestParam String reason,
      @RequestParam UUID doctorId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      RedirectAttributes ra) {
    queues.reorder(entryId, targetEntryId, after, reason);
    ra.addFlashAttribute("successKey", "appointment.flash.queueOverride");
    return "redirect:/queue/home?doctorId=" + doctorId + "&date=" + date;
  }

  @PostMapping("/triage")
  @PreAuthorize("hasRole('NURSE')")
  public String triage(
      @RequestParam UUID appointmentId,
      @RequestParam TriageLevel level,
      @RequestParam(required = false) String symptomSeverity,
      @RequestParam(required = false) String note,
      @RequestParam UUID doctorId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      RedirectAttributes ra) {
    triage.setLevel(appointmentId, level, symptomSeverity, note);
    ra.addFlashAttribute("successKey", "appointment.flash.triaged");
    return "redirect:/queue/home?doctorId=" + doctorId + "&date=" + date;
  }
}

