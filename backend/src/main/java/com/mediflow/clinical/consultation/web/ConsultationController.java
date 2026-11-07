package com.mediflow.clinical.consultation.web;

import com.mediflow.clinical.consultation.service.ConsultationService;
import com.mediflow.clinical.consultation.service.ConsultationService.ConsultationView;
import com.mediflow.clinical.vitals.service.VitalCatalogService;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.VitalObservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clinical/consultations")
public class ConsultationController {

  private final ConsultationService consultations;
  private final VitalCatalogService catalog;

  public ConsultationController(ConsultationService consultations, VitalCatalogService catalog) {
    this.consultations = consultations;
    this.catalog = catalog;
  }

  @GetMapping
  public String list(Model model) {
    model.addAttribute("pageTitleKey", "clinical.consult.list.title");
    model.addAttribute("encounters", consultations.listOpenForCurrentDoctor());
    model.addAttribute("labBadgeCount", consultations.unreadLabBadgeCount());
    return "clinical/consultation-list";
  }

  @PostMapping("/from-appointment/{appointmentId}")
  public String openFromAppointment(@PathVariable UUID appointmentId) {
    Encounter enc = consultations.openOrCreateFromAppointment(appointmentId);
    return "redirect:/clinical/consultations/" + enc.getId();
  }

  @GetMapping("/{encounterId}")
  public String show(@PathVariable UUID encounterId, Model model) {
    ConsultationView view = consultations.load(encounterId);
    model.addAttribute("pageTitleKey", "clinical.consult.title");
    model.addAttribute("view", view);
    model.addAttribute("labBadgeCount", consultations.unreadLabBadgeCount());
    model.addAttribute("trendSeries", buildTrendSeries(view));
    model.addAttribute("nurseTypes", catalog.nurseEntryTypes());
    return "clinical/consultation";
  }

  @PostMapping("/{encounterId}/notes")
  public String saveNote(
      @PathVariable UUID encounterId,
      @RequestParam String content,
      RedirectAttributes redirect) {
    consultations.saveNote(encounterId, content);
    redirect.addFlashAttribute("successKey", "clinical.consult.noteSaved");
    return "redirect:/clinical/consultations/" + encounterId;
  }

  @PostMapping("/{encounterId}/diagnosis")
  public String addDiagnosis(
      @PathVariable UUID encounterId,
      @RequestParam String icd10Code,
      RedirectAttributes redirect) {
    consultations.addDiagnosis(encounterId, icd10Code);
    redirect.addFlashAttribute("successKey", "clinical.consult.dxSaved");
    return "redirect:/clinical/consultations/" + encounterId;
  }

  @PostMapping("/{encounterId}/allergies/{allergyId}/confirm")
  public String confirmAllergy(
      @PathVariable UUID encounterId,
      @PathVariable UUID allergyId,
      RedirectAttributes redirect) {
    consultations.confirmAllergy(allergyId);
    redirect.addFlashAttribute("successKey", "clinical.consult.allergyConfirmed");
    return "redirect:/clinical/consultations/" + encounterId;
  }

  @PostMapping("/{encounterId}/lab-order")
  public String orderLab(
      @PathVariable UUID encounterId,
      @RequestParam String testCode,
      RedirectAttributes redirect) {
    consultations.orderLab(encounterId, testCode);
    redirect.addFlashAttribute("successKey", "clinical.consult.labRequested");
    return "redirect:/clinical/consultations/" + encounterId;
  }

  @GetMapping("/{encounterId}/summary")
  public String summary(@PathVariable UUID encounterId, Model model) {
    model.addAttribute("pageTitleKey", "clinical.consult.summary.title");
    model.addAttribute("summary", consultations.patientSummary(encounterId));
    model.addAttribute("encounterId", encounterId);
    return "clinical/patient-summary";
  }

  private Map<String, List<Map<String, Object>>> buildTrendSeries(ConsultationView view) {
    Map<String, List<Map<String, Object>>> series = new LinkedHashMap<>();
    for (VitalObservation obs : view.vitals()) {
      if (obs.getValueNumeric() == null) {
        continue;
      }
      series
          .computeIfAbsent(obs.getVitalType(), k -> new ArrayList<>())
          .add(
              Map.of(
                  "t", obs.getRecordedAt().toString(),
                  "v", obs.getValueNumeric(),
                  "c", obs.getClassification().name()));
    }
    return series;
  }
}
