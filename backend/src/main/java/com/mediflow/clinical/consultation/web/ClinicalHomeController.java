package com.mediflow.clinical.consultation.web;

import com.mediflow.clinical.consultation.service.ConsultationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Module home tiles for doctor / nurse dashboards. V2-01 may {@code th:replace} these once wired;
 * the fragment is also reachable standalone for preview.
 */
@Controller
@RequestMapping("/clinical/home")
public class ClinicalHomeController {

  private final ConsultationService consultations;

  public ClinicalHomeController(ConsultationService consultations) {
    this.consultations = consultations;
  }

  @GetMapping
  public String home(Model model) {
    model.addAttribute("pageTitleKey", "clinical.home.title");
    model.addAttribute("openToday", consultations.listOpenTodayForCurrentDoctor());
    model.addAttribute("vitalsPending", consultations.listVitalsPending(10));
    model.addAttribute("labBadgeCount", consultations.unreadLabBadgeCount());
    return "clinical/home-tiles";
  }
}
