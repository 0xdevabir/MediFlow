package com.mediflow.appointment.web;

import com.mediflow.appointment.redalert.RedAlertExt;
import com.mediflow.appointment.redalert.RedAlertService;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.RedAlert;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
@RequestMapping("/er")
@PreAuthorize("hasAnyRole('ER','ADMIN')")
public class ErController {

  private final RedAlertService redAlerts;
  private final ApptPatientRepository patients;

  public ErController(RedAlertService redAlerts, ApptPatientRepository patients) {
    this.redAlerts = redAlerts;
    this.patients = patients;
  }

  @GetMapping({"", "/", "/home"})
  public String board(@RequestParam(required = false) UUID hospitalId, Model model) {
    List<RedAlert> active = redAlerts.active(hospitalId);
    Map<UUID, Patient> byPatient = new HashMap<>();
    Map<UUID, RedAlertExt> ext = new HashMap<>();
    Map<UUID, String> assigneeNames = new HashMap<>();
    for (RedAlert a : active) {
      patients.findById(a.getPatientId()).ifPresent(p -> byPatient.put(a.getPatientId(), p));
      redAlerts
          .ext(a.getId())
          .ifPresent(
              e -> {
                ext.put(a.getId(), e);
                if (e.assignedErUserId() != null) {
                  String name = redAlerts.assigneeName(e.assignedErUserId());
                  if (name != null) {
                    assigneeNames.put(e.assignedErUserId(), name);
                  }
                }
              });
    }
    model.addAttribute("alerts", active);
    model.addAttribute("patients", byPatient);
    model.addAttribute("ext", ext);
    model.addAttribute("assigneeNames", assigneeNames);
    model.addAttribute("hospitalId", hospitalId);
    model.addAttribute("activeCount", active.size());
    return "er/board";
  }

  @GetMapping("/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    RedAlert alert =
        redAlerts
            .get(id)
            .orElseThrow(
                () ->
                    new com.mediflow.appointment.support.AppointmentException(
                        "appointment.error.redAlertNotFound"));
    RedAlertExt extension = redAlerts.ext(id).orElse(null);
    model.addAttribute("alert", alert);
    model.addAttribute("ext", extension);
    if (extension != null && extension.assignedErUserId() != null) {
      model.addAttribute("assigneeName", redAlerts.assigneeName(extension.assignedErUserId()));
    }
    patients.findById(alert.getPatientId()).ifPresent(p -> model.addAttribute("patient", p));
    return "er/detail";
  }

  @PostMapping("/{id}/acknowledge")
  @PreAuthorize("hasRole('ER')")
  public String acknowledge(@PathVariable UUID id, RedirectAttributes ra) {
    redAlerts.acknowledge(id);
    ra.addFlashAttribute("successKey", "appointment.flash.redAlertAck");
    return "redirect:/er/" + id;
  }

  @PostMapping("/{id}/handover")
  @PreAuthorize("hasRole('ER')")
  public String handover(
      @PathVariable UUID id,
      @RequestParam String status,
      @RequestParam(required = false) String note,
      RedirectAttributes ra) {
    redAlerts.handover(id, status, note);
    ra.addFlashAttribute("successKey", "appointment.flash.redAlertHandover");
    return "redirect:/er";
  }
}
