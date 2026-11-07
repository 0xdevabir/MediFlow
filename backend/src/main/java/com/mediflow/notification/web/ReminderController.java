package com.mediflow.notification.web;

import com.mediflow.notification.domain.AdherenceOutcome;
import com.mediflow.notification.service.AdherenceView;
import com.mediflow.notification.service.ReminderService;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Patient's own medicine reminders, acknowledgement and adherence %. */
@Controller
@RequestMapping("/notifications/reminders")
@PreAuthorize("hasRole('PATIENT')")
public class ReminderController {

  private final ReminderService reminders;
  private final AdherenceView adherence;
  private final CurrentUser currentUser;
  private final AuditPort audit;

  public ReminderController(
      ReminderService reminders, AdherenceView adherence, CurrentUser currentUser, AuditPort audit) {
    this.reminders = reminders;
    this.adherence = adherence;
    this.currentUser = currentUser;
    this.audit = audit;
  }

  @GetMapping
  public String list(Model model) {
    UUID patientId = adherence.currentPatientId().orElse(null);
    model.addAttribute("hasPatient", patientId != null);
    if (patientId != null) {
      String locale = LocaleContextHolder.getLocale().getLanguage();
      model.addAttribute("rows", reminders.medicationRows(patientId, locale));
      model.addAttribute("adherence", reminders.adherencePercent(patientId).orElse(null));
    }
    return "notification/reminders";
  }

  @PostMapping("/{id}/ack")
  public String acknowledge(
      @PathVariable UUID id, @RequestParam AdherenceOutcome outcome, RedirectAttributes ra) {
    UUID userId = currentUser.requireUserId();
    UUID patientId = adherence.currentPatientId().orElse(null);
    try {
      if (patientId == null) {
        throw new IllegalArgumentException("No patient record");
      }
      reminders.acknowledge(id, patientId, outcome);
      audit.record(
          userId, patientId, "REMINDER_ACKNOWLEDGE", "Reminder", id, Map.of("outcome", outcome.name()));
      ra.addFlashAttribute("successKey", "notification.reminders.acknowledged");
    } catch (IllegalArgumentException e) {
      ra.addFlashAttribute("errorKey", "notification.error.notFound");
    }
    return "redirect:/notifications/reminders";
  }
}
