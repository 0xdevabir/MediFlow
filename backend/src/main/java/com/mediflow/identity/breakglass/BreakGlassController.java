package com.mediflow.identity.breakglass;

import com.mediflow.identity.mfa.SafeRedirect;
import com.mediflow.shared.domain.BreakGlassAccess;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Break-glass form (other modules link to {@code /identity/break-glass/new?patientId=..&continue=..}
 * from their 403 / "not assigned" pages) and the admin review list.
 */
@Controller
@RequestMapping("/identity/break-glass")
public class BreakGlassController {

  private final BreakGlassService service;

  public BreakGlassController(BreakGlassService service) {
    this.service = service;
  }

  @GetMapping("/new")
  @PreAuthorize("hasAnyRole('DOCTOR','ER')")
  public String form(
      @RequestParam("patientId") UUID patientId,
      @RequestParam(name = "continue", required = false) String continueUrl,
      Model model) {
    model.addAttribute("patientId", patientId);
    model.addAttribute("continueUrl", SafeRedirect.localOrRoot(continueUrl));
    model.addAttribute("minLength", BreakGlassService.MIN_REASON_LENGTH);
    service
        .activeForCurrentUser(patientId)
        .ifPresent(
            a -> {
              model.addAttribute("active", a);
              model.addAttribute(
                  "activeUntil",
                  java.time.LocalDateTime.ofInstant(
                      a.getExpiresAt(), com.mediflow.identity.access.AccessRules.CLINIC_ZONE));
            });
    return "identity/break-glass-new";
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('DOCTOR','ER')")
  public String activate(
      @RequestParam("patientId") UUID patientId,
      @RequestParam("reason") String reason,
      @RequestParam(name = "continue", required = false) String continueUrl,
      RedirectAttributes flash) {
    try {
      BreakGlassAccess access = service.activate(patientId, reason);
      flash.addFlashAttribute("successKey", "identity.breakglass.activated");
      flash.addFlashAttribute("breakGlassExpiresAt", access.getExpiresAt());
      return "redirect:" + SafeRedirect.localOrRoot(continueUrl);
    } catch (IllegalArgumentException e) {
      flash.addFlashAttribute("errorKey", e.getMessage());
      return "redirect:/identity/break-glass/new?patientId=" + patientId;
    }
  }

  @GetMapping("/review")
  @PreAuthorize("hasRole('ADMIN')")
  public String reviewList(Model model) {
    model.addAttribute("items", service.listForReview());
    model.addAttribute("outcomes", BreakGlassReview.Outcome.values());
    return "identity/break-glass-review";
  }

  @PostMapping("/{id}/review")
  @PreAuthorize("hasRole('ADMIN')")
  public String review(
      @PathVariable("id") UUID id,
      @RequestParam("outcome") BreakGlassReview.Outcome outcome,
      @RequestParam(name = "note", required = false) String note,
      RedirectAttributes flash) {
    try {
      service.review(id, outcome, note);
      flash.addFlashAttribute("successKey", "identity.breakglass.review.saved");
    } catch (IllegalArgumentException | IllegalStateException e) {
      flash.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/identity/break-glass/review";
  }
}
