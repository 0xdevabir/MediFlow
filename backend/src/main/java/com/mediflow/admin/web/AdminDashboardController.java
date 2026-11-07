package com.mediflow.admin.web;

import com.mediflow.audit.service.AuditAnomalyService;
import com.mediflow.audit.service.AuditChainService;
import com.mediflow.shared.web.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Admin home. Links to directory (T03), identity break-glass review (T01), audit, settings,
 * analytics and DHIS2 export. Does not surface clinical record content.
 */
@Controller
public class AdminDashboardController {

  private final AuditChainService chain;
  private final AuditAnomalyService anomalies;
  private final CurrentUser currentUser;

  public AdminDashboardController(
      AuditChainService chain, AuditAnomalyService anomalies, CurrentUser currentUser) {
    this.chain = chain;
    this.anomalies = anomalies;
    this.currentUser = currentUser;
  }

  @GetMapping("/admin")
  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
  public String home(Model model) {
    model.addAttribute("isAdmin", currentUser.hasRole("ADMIN"));
    model.addAttribute("lastCheck", chain.lastCheck().orElse(null));
    if (currentUser.hasRole("ADMIN")) {
      model.addAttribute("openAnomalies", anomalies.list(true).size());
    }
    return "admin/dashboard";
  }
}
