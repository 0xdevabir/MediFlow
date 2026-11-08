package com.mediflow.admin.web;

import com.mediflow.admin.settings.SettingGovernanceService;
import com.mediflow.audit.service.AuditAnomalyService;
import com.mediflow.audit.service.AuditChainService;
import com.mediflow.shared.web.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin / clinical-approver home. Approvers see the pending dual-approval queue first and never
 * receive audit-anomaly or patient-touch model attributes. Staff CRUD lives under identity.
 */
@Controller
public class AdminDashboardController {

  private final AuditChainService chain;
  private final AuditAnomalyService anomalies;
  private final SettingGovernanceService governance;
  private final CurrentUser currentUser;

  public AdminDashboardController(
      AuditChainService chain,
      AuditAnomalyService anomalies,
      SettingGovernanceService governance,
      CurrentUser currentUser) {
    this.chain = chain;
    this.anomalies = anomalies;
    this.governance = governance;
    this.currentUser = currentUser;
  }

  @GetMapping("/admin")
  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
  public String home(Model model) {
    boolean isAdmin = currentUser.hasRole("ADMIN");
    boolean isApprover = currentUser.hasRole("CLINICAL_APPROVER");
    model.addAttribute("isAdmin", isAdmin);
    model.addAttribute("isApprover", isApprover);
    model.addAttribute("pending", governance.pending());
    // Approver-only home: dual-approval cues, no audit/PHI widgets.
    if (isApprover && !isAdmin) {
      model.addAttribute("pageTitleKey", "admin.approver.title");
      model.addAttribute("pageSubtitleKey", "admin.approver.intro");
    } else {
      model.addAttribute("pageTitleKey", "admin.dashboard.title");
      model.addAttribute("pageSubtitleKey", "admin.dashboard.intro");
    }
    // Chain integrity + anomaly counts are admin-only (patient-touch aggregates).
    if (isAdmin) {
      model.addAttribute("lastCheck", chain.lastCheck().orElse(null));
      model.addAttribute("openAnomalies", anomalies.list(true).size());
    } else {
      model.addAttribute("lastCheck", null);
      model.addAttribute("openAnomalies", 0);
    }
    return "admin/dashboard";
  }

  @PostMapping("/admin/approvals/{id}/approve")
  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
  public String approve(@PathVariable UUID id, RedirectAttributes ra) {
    try {
      governance.approve(id);
      ra.addFlashAttribute("successKey", "admin.settings.approved");
    } catch (IllegalArgumentException | AccessDeniedException e) {
      ra.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/admin";
  }

  @PostMapping("/admin/approvals/{id}/reject")
  @PreAuthorize("hasAnyRole('ADMIN','CLINICAL_APPROVER')")
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
    return "redirect:/admin";
  }
}

