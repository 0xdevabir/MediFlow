package com.mediflow.clinical.vitals.web;

import com.mediflow.clinical.vitals.domain.VitalParseException;
import com.mediflow.clinical.vitals.domain.VitalReading;
import com.mediflow.clinical.vitals.domain.VitalSetResult;
import com.mediflow.clinical.vitals.service.VitalCatalogService;
import com.mediflow.clinical.vitals.service.VitalEntryService;
import com.mediflow.shared.domain.VitalObservation;
import com.mediflow.shared.repo.EncounterRepository;
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
@RequestMapping("/clinical/vitals")
public class VitalsController {

  private final VitalEntryService vitals;
  private final VitalCatalogService catalog;
  private final EncounterRepository encounters;

  public VitalsController(
      VitalEntryService vitals, VitalCatalogService catalog, EncounterRepository encounters) {
    this.vitals = vitals;
    this.catalog = catalog;
    this.encounters = encounters;
  }

  @GetMapping("/entry/{encounterId}")
  public String entryForm(@PathVariable UUID encounterId, Model model) {
    encounters
        .findById(encounterId)
        .orElseThrow(() -> new IllegalArgumentException("Encounter not found"));
    model.addAttribute("pageTitleKey", "clinical.vitals.entry.title");
    model.addAttribute("encounterId", encounterId);
    model.addAttribute("types", catalog.nurseEntryTypes());
    model.addAttribute("values", Map.of());
    return "clinical/vitals-entry";
  }

  @PostMapping("/entry/{encounterId}")
  public String record(
      @PathVariable UUID encounterId,
      @RequestParam Map<String, String> form,
      @RequestParam(name = "preview", required = false) String preview,
      Model model,
      RedirectAttributes redirect) {
    Map<String, String> raw = extractRaw(form);
    try {
      if (preview != null) {
        VitalSetResult result = vitals.preview(encounterId, raw);
        model.addAttribute("pageTitleKey", "clinical.vitals.entry.title");
        model.addAttribute("encounterId", encounterId);
        model.addAttribute("types", catalog.nurseEntryTypes());
        model.addAttribute("values", raw);
        model.addAttribute("result", result);
        model.addAttribute("colours", colours(result));
        return "clinical/vitals-entry";
      }
      VitalSetResult result = vitals.record(encounterId, raw);
      redirect.addFlashAttribute("successKey", "clinical.vitals.saved");
      redirect.addFlashAttribute("patientLevel", result.patientLevel().name());
      return "redirect:/clinical/vitals/entry/" + encounterId + "?saved=1&level=" + result.patientLevel();
    } catch (VitalParseException e) {
      model.addAttribute("pageTitleKey", "clinical.vitals.entry.title");
      model.addAttribute("encounterId", encounterId);
      model.addAttribute("types", catalog.nurseEntryTypes());
      model.addAttribute("values", raw);
      model.addAttribute("parseErrorKey", e.messageKey());
      model.addAttribute("parseErrorArgs", e.args());
      model.addAttribute("parseErrorVital", e.vitalCode());
      return "clinical/vitals-entry";
    }
  }

  @GetMapping("/trend/{patientId}")
  public String trend(
      @PathVariable UUID patientId,
      @RequestParam(defaultValue = "HR") String type,
      @RequestParam(defaultValue = "14") int days,
      Model model) {
    List<VitalObservation> points = vitals.trend(patientId, type, days);
    model.addAttribute("pageTitleKey", "clinical.vitals.trend.title");
    model.addAttribute("patientId", patientId);
    model.addAttribute("type", type);
    model.addAttribute("days", days);
    model.addAttribute("points", points);
    model.addAttribute("types", catalog.nurseEntryTypes());
    return "clinical/vitals-trend";
  }

  private static Map<String, String> extractRaw(Map<String, String> form) {
    Map<String, String> raw = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : form.entrySet()) {
      if (e.getKey().startsWith("vital_")) {
        raw.put(e.getKey().substring("vital_".length()), e.getValue());
      }
    }
    return raw;
  }

  private static Map<String, String> colours(VitalSetResult result) {
    Map<String, String> map = new LinkedHashMap<>();
    for (VitalReading r : result.readings()) {
      map.put(r.code(), VitalEntryService.ClassColour.of(r.level()).cssClass());
    }
    map.put("_patient", VitalEntryService.ClassColour.of(result.patientLevel()).cssClass());
    return map;
  }
}

