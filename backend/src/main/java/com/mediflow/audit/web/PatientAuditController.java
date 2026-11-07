package com.mediflow.audit.web;

import com.mediflow.audit.service.AuditQueryService;
import com.mediflow.shared.web.CurrentUser;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Patient portal: who accessed my record. */
@Controller
public class PatientAuditController {

  private final AuditQueryService queries;
  private final CurrentUser currentUser;

  public PatientAuditController(AuditQueryService queries, CurrentUser currentUser) {
    this.queries = queries;
    this.currentUser = currentUser;
  }

  @GetMapping("/audit/me")
  @PreAuthorize("hasRole('PATIENT')")
  public String me(Model model) {
    UUID userId = currentUser.requireUserId();
    UUID patientId =
        queries
            .patientIdForUser(userId)
            .orElseThrow(() -> new IllegalArgumentException("audit.me.noPatient"));
    model.addAttribute("rows", queries.forPatient(patientId, userId));
    model.addAttribute("pageTitleKey", "audit.me.title");
    return "audit/me";
  }
}
