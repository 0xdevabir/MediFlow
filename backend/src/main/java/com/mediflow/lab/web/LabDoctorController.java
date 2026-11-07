package com.mediflow.lab.web;

import com.mediflow.lab.service.LabCatalogService;
import com.mediflow.lab.service.LabOrderService;
import com.mediflow.lab.service.LabQueryService;
import java.util.List;
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

@Controller
@RequestMapping("/lab/encounters")
@PreAuthorize("hasRole('DOCTOR')")
public class LabDoctorController {

  private final LabCatalogService catalog;
  private final LabOrderService orders;
  private final LabQueryService queries;

  public LabDoctorController(
      LabCatalogService catalog, LabOrderService orders, LabQueryService queries) {
    this.catalog = catalog;
    this.orders = orders;
    this.queries = queries;
  }

  @GetMapping("/{encounterId}/orders")
  public String form(@PathVariable UUID encounterId, Model model) {
    model.addAttribute("encounterId", encounterId);
    model.addAttribute("catalog", catalog.doctorCatalog());
    model.addAttribute("orders", queries.ordersForEncounter(encounterId));
    return "lab/doctor-order";
  }

  @PostMapping("/{encounterId}/orders")
  public String order(
      @PathVariable UUID encounterId,
      @RequestParam(required = false) List<UUID> testIds,
      RedirectAttributes flash) {
    orders.orderForEncounter(encounterId, testIds == null ? List.of() : testIds);
    flash.addFlashAttribute("successKey", "lab.doctor.ordered");
    return "redirect:/lab/encounters/" + encounterId + "/orders";
  }
}
