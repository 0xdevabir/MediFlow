package com.mediflow.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PlaceholderController {

  // Real modules now own their root URLs; only legacy aliases remain here.
  @GetMapping("/consultations")
  public String consultationsAlias() {
    return "redirect:/clinical/consultations";
  }

  /**
   * Legacy {@code /modules/{module}} entry points. Known hubs redirect; unknown modules 404.
   */
  @GetMapping("/modules/{module}")
  public String named(@PathVariable String module) {
    return switch (module) {
      case "patients", "patient" -> "redirect:/patients";
      case "directory" -> "redirect:/directory";
      case "appointments", "appointment" -> "redirect:/appointments";
      case "queue" -> "redirect:/queue";
      case "er", "red-alert", "redalert" -> "redirect:/er";
      case "consultations", "clinical", "consultation" -> "redirect:/clinical/consultations";
      case "prescriptions", "prescription", "rx" -> "redirect:/prescriptions";
      case "lab" -> "redirect:/lab";
      case "pharmacy" -> "redirect:/pharmacy";
      case "admin" -> "redirect:/admin";
      case "audit" -> "redirect:/audit/me";
      case "notifications", "notification" -> "redirect:/notifications/call-list";
      case "identity", "staff" -> "redirect:/identity/staff";
      case "fhir" -> "redirect:/fhir/r4/metadata";
      default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    };
  }
}
