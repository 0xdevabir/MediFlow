package com.mediflow.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PlaceholderController {

  @GetMapping({
    "/patients",
    "/directory",
    "/appointments",
    "/queue",
    "/consultations",
    "/prescriptions",
    "/lab",
    "/pharmacy",
    "/admin",
    "/audit/me"
  })
  public String moduleRoot(Model model) {
    model.addAttribute("pageTitleKey", "comingsoon.title");
    model.addAttribute("modulePath", "module");
    return "coming-soon";
  }

  @GetMapping("/modules/{module}")
  public String named(@PathVariable String module, Model model) {
    model.addAttribute("pageTitleKey", "comingsoon.title");
    model.addAttribute("modulePath", module);
    return "coming-soon";
  }
}
