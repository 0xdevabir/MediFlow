package com.mediflow.patient.web;

import com.mediflow.patient.service.PatientRegistrationService;
import com.mediflow.shared.domain.Patient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/patients")
public class PatientRegistrationController {

  private final PatientRegistrationService registration;

  public PatientRegistrationController(PatientRegistrationService registration) {
    this.registration = registration;
  }

  @GetMapping("/register")
  public String selfForm(Model model) {
    model.addAttribute("form", new RegistrationForm());
    model.addAttribute("pageTitleKey", "patient.register.title");
    model.addAttribute("selfSignup", true);
    return "patient/register";
  }

  @PostMapping("/register")
  public String selfSubmit(
      @ModelAttribute("form") RegistrationForm form, RedirectAttributes flash) {
    Patient patient =
        registration.registerSelf(
            form.toNewPatient(), form.getUsername(), form.getPassword(), form.isConsent());
    flash.addFlashAttribute("successKey", "patient.register.success");
    return "redirect:/login?registered=" + patient.getMrn();
  }

  @GetMapping("/walk-in")
  public String walkInForm(Model model) {
    model.addAttribute("form", new RegistrationForm());
    model.addAttribute("pageTitleKey", "patient.walkin.title");
    model.addAttribute("selfSignup", false);
    return "patient/register";
  }

  @PostMapping("/walk-in")
  public String walkInSubmit(
      @ModelAttribute("form") RegistrationForm form, RedirectAttributes flash) {
    Patient patient = registration.registerWalkIn(form.toNewPatient(), form.isConsent());
    flash.addFlashAttribute("successKey", "patient.walkin.success");
    return "redirect:/patients/" + patient.getId();
  }
}
