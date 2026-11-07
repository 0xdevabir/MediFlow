package com.mediflow.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PlaceholderController {

  // Real modules now own their root URLs; only legacy aliases remain here.
  @GetMapping("/consultations")
  public String consultationsAlias() {
    return "redirect:/clinical/consultations";
  }

  @GetMapping("/modules/{module}")
  public String named(@PathVariable String module, Model model) {
    model.addAttribute("pageTitleKey", "comingsoon.title");
    model.addAttribute("modulePath", module);
    return "coming-soon";
  }
}
