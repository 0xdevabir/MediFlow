package com.mediflow.admin.web;

import com.mediflow.admin.settings.GovernedKeys;
import com.mediflow.admin.settings.SettingGovernanceService;
import com.mediflow.shared.web.CurrentUser;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
@PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
public class AdminSettingsController {

  private final SettingGovernanceService governance;
  private final CurrentUser currentUser;

  public AdminSettingsController(SettingGovernanceService governance, CurrentUser currentUser) {
    this.governance = governance;
    this.currentUser = currentUser;
  }

  @GetMapping
  public String page(Model model) {
    model.addAttribute("settings", governance.latestSettings());
    model.addAttribute("pending", governance.pending());
    model.addAttribute("isAdmin", currentUser.hasRole("ADMIN"));
    model.addAttribute("aiKey", GovernedKeys.AI_CONFIDENCE_THRESHOLD);
    return "admin/settings";
  }

  @PostMapping("/direct")
  @PreAuthorize("hasRole('ADMIN')")
  public String direct(
      @RequestParam String key, @RequestParam String value, RedirectAttributes ra) {
    try {
      governance.setDirect(key, value);
      ra.addFlashAttribute("successKey", "admin.settings.saved");
    } catch (IllegalArgumentException e) {
      ra.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/admin/settings";
  }

  @PostMapping("/request")
  public String request(
      @RequestParam String key, @RequestParam String value, RedirectAttributes ra) {
    try {
      governance.requestChange(key, value);
      ra.addFlashAttribute("successKey", "admin.settings.requested");
    } catch (IllegalArgumentException e) {
      ra.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/admin/settings";
  }

  @PostMapping("/{id}/approve")
  public String approve(@PathVariable UUID id, RedirectAttributes ra) {
    try {
      governance.approve(id);
      ra.addFlashAttribute("successKey", "admin.settings.approved");
    } catch (IllegalArgumentException | AccessDeniedException e) {
      ra.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/admin/settings";
  }

  @PostMapping("/{id}/reject")
  public String reject(
      @PathVariable UUID id,
      @RequestParam(required = false) String reason,
      RedirectAttributes ra) {
    try {
      governance.reject(id, reason);
      ra.addFlashAttribute("successKey", "admin.settings.rejected");
    } catch (IllegalArgumentException | AccessDeniedException e) {
      ra.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/admin/settings";
  }
}
