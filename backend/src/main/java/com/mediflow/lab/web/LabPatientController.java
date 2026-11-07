package com.mediflow.lab.web;

import com.mediflow.lab.service.LabQueryService;
import com.mediflow.lab.service.LabViews;
import com.mediflow.shared.domain.OrderType;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lab/reports")
public class LabPatientController {

  private final LabQueryService queries;

  public LabPatientController(LabQueryService queries) {
    this.queries = queries;
  }

  @PreAuthorize("hasRole('PATIENT')")
  @GetMapping
  public String list(Model model) {
    model.addAttribute("orders", queries.myOrders());
    return "lab/reports";
  }

  @PreAuthorize("hasAnyRole('PATIENT','DOCTOR','LAB_TECH','FRONT_DESK','ADMIN')")
  @GetMapping("/{orderId}")
  public String detail(@PathVariable UUID orderId, Model model) {
    LabViews.ReportView report = queries.report(orderId);
    model.addAttribute("report", report);
    model.addAttribute(
        "showSeeDoctor",
        report.released()
            && report.abnormal()
            && report.order().orderType() == OrderType.DIRECT);
    return "lab/report-detail";
  }
}
