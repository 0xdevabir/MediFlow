package com.mediflow.lab.web;

import com.mediflow.shared.web.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lab")
public class LabHomeController {

  private final CurrentUser currentUser;

  public LabHomeController(CurrentUser currentUser) {
    this.currentUser = currentUser;
  }

  @PreAuthorize("isAuthenticated()")
  @GetMapping({"", "/", "/home", "/index"})
  public String home(Model model) {
    model.addAttribute("isPatient", currentUser.hasRole("PATIENT"));
    model.addAttribute("isLabTech", currentUser.hasRole("LAB_TECH"));
    model.addAttribute("isFrontDesk", currentUser.hasRole("FRONT_DESK"));
    model.addAttribute("isDoctor", currentUser.hasRole("DOCTOR"));
    return "lab/home";
  }
}
