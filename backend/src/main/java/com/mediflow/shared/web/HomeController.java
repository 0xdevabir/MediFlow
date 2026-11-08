package com.mediflow.shared.web;

import com.mediflow.shared.port.AdherenceQueryPort;
import com.mediflow.shared.repo.PatientRepository;
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

  public HomeController(
      CurrentUser currentUser,
      PatientRepository patients,
      ObjectProvider<AdherenceQueryPort> adherence) {
    this.currentUser = currentUser;
    this.patients = patients;
    this.adherence = adherence;
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
    if (currentUser.hasRole("PATIENT")) {
      Optional<UUID> patientId =
          currentUser.userId().flatMap(patients::findByUserId).map(p -> p.getId());
      model.addAttribute("patientId", patientId.orElse(null));
      AdherenceQueryPort port = adherence.getIfAvailable();
      if (port != null && patientId.isPresent()) {
        adherencePercent = port.adherencePercent(patientId.get());
      }
    }
    model.addAttribute("adherencePercent", adherencePercent.orElse(null));
    model.addAttribute("adherenceConfigured", adherence.getIfAvailable() != null);

    return "home";
  }
}
