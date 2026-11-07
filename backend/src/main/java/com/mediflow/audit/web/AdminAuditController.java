package com.mediflow.audit.web;

import com.mediflow.audit.service.AuditAnomalyService;
import com.mediflow.audit.service.AuditChainService;
import com.mediflow.audit.service.AuditExportService;
import com.mediflow.audit.service.AuditQueryService;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin audit console: search, chain integrity, anomalies, CSV export. */
@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

  private final AuditQueryService queries;
  private final AuditChainService chain;
  private final AuditAnomalyService anomalies;
  private final AuditExportService export;
  private final CurrentUser currentUser;

  public AdminAuditController(
      AuditQueryService queries,
      AuditChainService chain,
      AuditAnomalyService anomalies,
      AuditExportService export,
      CurrentUser currentUser) {
    this.queries = queries;
    this.chain = chain;
    this.anomalies = anomalies;
    this.export = export;
    this.currentUser = currentUser;
  }

  @GetMapping
  public String index(Model model) {
    model.addAttribute("lastCheck", chain.lastCheck().orElse(null));
    model.addAttribute("openAnomalies", anomalies.list(true).size());
    model.addAttribute("retentionYears", export.retentionYears());
    return "audit/admin-index";
  }

  @GetMapping("/search")
  public String search(
      @RequestParam(required = false) UUID actorUserId,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Model model) {
    Instant fromInst = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant toInst =
        to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var filter = new AuditQueryService.SearchFilter(actorUserId, patientId, action, fromInst, toInst, 200);
    model.addAttribute("rows", queries.search(filter, currentUser.requireUserId()));
    model.addAttribute("actorUserId", actorUserId);
    model.addAttribute("patientId", patientId);
    model.addAttribute("action", action);
    model.addAttribute("from", from);
    model.addAttribute("to", to);
    return "audit/search";
  }

  @GetMapping("/chain")
  public String chain(Model model) {
    model.addAttribute("lastCheck", chain.lastCheck().orElse(null));
    model.addAttribute("recent", chain.recentChecks(20));
    return "audit/chain";
  }

  @PostMapping("/chain/verify")
  public String verify(RedirectAttributes ra) {
    var check = chain.verifyAsAdmin(currentUser.requireUserId());
    ra.addFlashAttribute(
        check.ok() ? "successMessage" : "errorMessage", check.message());
    return "redirect:/admin/audit/chain";
  }

  @GetMapping("/anomalies")
  public String anomalies(
      @RequestParam(defaultValue = "true") boolean openOnly, Model model) {
    model.addAttribute("rows", anomalies.list(openOnly));
    model.addAttribute("openOnly", openOnly);
    return "audit/anomalies";
  }

  @PostMapping("/anomalies/{id}/review")
  public String reviewAnomaly(
      @PathVariable UUID id,
      @RequestParam(required = false) String note,
      RedirectAttributes ra) {
    try {
      anomalies.review(id, currentUser.requireUserId(), note);
      ra.addFlashAttribute("successKey", "audit.anomaly.reviewed");
    } catch (IllegalArgumentException e) {
      ra.addFlashAttribute("errorKey", e.getMessage());
    }
    return "redirect:/admin/audit/anomalies";
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> exportCsv(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    Instant fromInst = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant toInst =
        to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var result = export.exportCsv(fromInst, toInst, currentUser.requireUserId());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"audit-export.csv\"")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(result.csv());
  }
}
