package com.mediflow.admin.web;

import com.mediflow.admin.analytics.AnalyticsService;
import com.mediflow.admin.analytics.Dhis2ExportService;
import com.mediflow.shared.web.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

  private final AnalyticsService analytics;
  private final Dhis2ExportService dhis2;
  private final CurrentUser currentUser;

  public AdminAnalyticsController(
      AnalyticsService analytics, Dhis2ExportService dhis2, CurrentUser currentUser) {
    this.analytics = analytics;
    this.dhis2 = dhis2;
    this.currentUser = currentUser;
  }

  @GetMapping("/analytics")
  public String analytics(Model model) {
    model.addAttribute("kpis", analytics.dashboard(currentUser.requireUserId()));
    return "admin/analytics";
  }

  @GetMapping("/dhis2")
  public String dhis2Page(Model model) {
    model.addAttribute("pushDisabled", true);
    return "admin/dhis2";
  }

  @GetMapping("/dhis2/export")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
    var result = dhis2.exportMonth(month, currentUser.requireUserId());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + Dhis2ExportService.filename(result.period()) + "\"")
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.json());
  }
}
