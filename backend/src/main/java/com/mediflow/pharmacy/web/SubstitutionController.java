package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.dispensing.PharmacyException;
import com.mediflow.pharmacy.dispensing.PharmacyRequestService;
import com.mediflow.shared.web.CurrentUser;
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

/** Interim doctor UI for substitution approval until T06 owns it. */
@Controller
@RequestMapping("/pharmacy/substitutions")
@PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
public class SubstitutionController {

  private final PharmacyRequestService requests;
  private final CurrentUser currentUser;

  public SubstitutionController(PharmacyRequestService requests, CurrentUser currentUser) {
    this.requests = requests;
    this.currentUser = currentUser;
  }

  @GetMapping
  public String list(Model model) {
    model.addAttribute("pageTitleKey", "pharmacy.substitution.title");
    model.addAttribute("rows", requests.pendingForDoctor(currentUser.requireUserId()));
    return "pharmacy/substitutions";
  }

  @PostMapping("/{id}/decide")
  public String decide(
      @PathVariable UUID id,
      @RequestParam boolean approve,
      @RequestParam(required = false) String note,
      RedirectAttributes ra) {
    try {
      requests.decideSubstitution(id, approve, note, currentUser.requireUserId());
      ra.addFlashAttribute(
          "successKey",
          approve ? "pharmacy.substitution.approved" : "pharmacy.substitution.rejected");
    } catch (PharmacyException e) {
      ra.addFlashAttribute("errorKey", e.messageKey());
    }
    return "redirect:/pharmacy/substitutions";
  }
}
