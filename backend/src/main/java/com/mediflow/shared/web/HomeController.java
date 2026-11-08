package com.mediflow.shared.web;

import com.mediflow.clinical.consultation.service.ConsultationService;
import com.mediflow.patient.service.PatientAppointmentGlancePort;
import com.mediflow.shared.port.AdherenceQueryPort;
import com.mediflow.shared.repo.PatientRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

  private final CurrentUser currentUser;
  private final PatientRepository patients;
  private final ObjectProvider<AdherenceQueryPort> adherence;
  private final ObjectProvider<PatientAppointmentGlancePort> appointmentGlance;
  private final ObjectProvider<ConsultationService> consultations;

  public HomeController(
      CurrentUser currentUser,
      PatientRepository patients,
      ObjectProvider<AdherenceQueryPort> adherence,
      ObjectProvider<PatientAppointmentGlancePort> appointmentGlance,
      ObjectProvider<ConsultationService> consultations) {
    this.currentUser = currentUser;
    this.patients = patients;
    this.adherence = adherence;
    this.appointmentGlance = appointmentGlance;
    this.consultations = consultations;
  }

  @GetMapping("/")
  public String home(Model model) {
    model.addAttribute("pageTitleKey", "home.title");
    model.addAttribute("isPatient", currentUser.hasRole("PATIENT"));
    model.addAttribute("isFrontDesk", currentUser.hasRole("FRONT_DESK"));
    model.addAttribute("isNurse", currentUser.hasRole("NURSE"));
    model.addAttribute("isDoctor", currentUser.hasRole("DOCTOR"));
    model.addAttribute("isEr", currentUser.hasRole("ER"));
    model.addAttribute("isLabTech", currentUser.hasRole("LAB_TECH"));
    model.addAttribute("isPharmacist", currentUser.hasRole("PHARMACIST"));
    model.addAttribute("isAdmin", currentUser.hasRole("ADMIN"));
    model.addAttribute("isApprover", currentUser.hasRole("CLINICAL_APPROVER"));

    Optional<Integer> adherencePercent = Optional.empty();
    model.addAttribute("nextVisit", null);
    if (currentUser.hasRole("PATIENT")) {
      Optional<UUID> patientId =
          currentUser.userId().flatMap(patients::findByUserId).map(p -> p.getId());
      model.addAttribute("patientId", patientId.orElse(null));
      AdherenceQueryPort port = adherence.getIfAvailable();
      if (port != null && patientId.isPresent()) {
        adherencePercent = port.adherencePercent(patientId.get());
      }
      PatientAppointmentGlancePort glance = appointmentGlance.getIfAvailable();
      if (glance != null && patientId.isPresent()) {
        model.addAttribute("nextVisit", glance.nextUpcoming(patientId.get()).orElse(null));
      }
    }
    Integer pct = adherencePercent.orElse(null);
    model.addAttribute("adherencePercent", pct);
    model.addAttribute("pct", pct);
    model.addAttribute("adherenceConfigured", adherence.getIfAvailable() != null);

    ConsultationService clinical = consultations.getIfAvailable();
    if (clinical != null
        && (currentUser.hasRole("DOCTOR")
            || currentUser.hasRole("NURSE")
            || currentUser.hasRole("ADMIN")
            || currentUser.hasRole("ER"))) {
      model.addAttribute(
          "openToday",
          currentUser.hasRole("DOCTOR") || currentUser.hasRole("ADMIN")
              ? clinical.listOpenTodayForCurrentDoctor()
              : List.of());
      model.addAttribute("vitalsPending", clinical.listVitalsPending(10));
      model.addAttribute("labBadgeCount", clinical.unreadLabBadgeCount());
    } else {
      model.addAttribute("openToday", List.of());
      model.addAttribute("vitalsPending", List.of());
      model.addAttribute("labBadgeCount", 0L);
    }

    return "home";
  }
}
