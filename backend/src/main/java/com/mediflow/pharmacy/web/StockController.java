package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.dispensing.PharmacyException;
import com.mediflow.pharmacy.dispensing.PharmacyHospitalResolver;
import com.mediflow.pharmacy.stock.StockService;
import com.mediflow.shared.web.CurrentUser;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pharmacy/stock")
@PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
public class StockController {

  private final StockService stock;
  private final PharmacyHospitalResolver hospitals;
  private final CurrentUser currentUser;

  public StockController(
      StockService stock, PharmacyHospitalResolver hospitals, CurrentUser currentUser) {
    this.stock = stock;
    this.hospitals = hospitals;
    this.currentUser = currentUser;
  }

  @GetMapping
  public String list(Model model) {
    UUID hospitalId = hospitals.requireHospital(currentUser.requireUserId());
    model.addAttribute("pageTitleKey", "pharmacy.stock.title");
    model.addAttribute("hospitalId", hospitalId);
    model.addAttribute("rows", stock.list(hospitalId));
    model.addAttribute("lowOnly", false);
    return "pharmacy/stock";
  }

  @GetMapping("/low")
  public String low(Model model) {
    UUID hospitalId = hospitals.requireHospital(currentUser.requireUserId());
    model.addAttribute("pageTitleKey", "pharmacy.stock.lowTitle");
    model.addAttribute("hospitalId", hospitalId);
    model.addAttribute("rows", stock.lowStock(hospitalId));
    model.addAttribute("lowOnly", true);
    return "pharmacy/stock";
  }

  @PostMapping("/batch")
  public String upsertBatch(
      @RequestParam UUID brandId,
      @RequestParam String batchNo,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
      @RequestParam int quantity,
      @RequestParam(defaultValue = "10") int reorderLevel,
      RedirectAttributes ra) {
    try {
      UUID hospitalId = hospitals.requireHospital(currentUser.requireUserId());
      stock.upsertBatch(
          hospitalId,
          brandId,
          batchNo,
          expiryDate,
          quantity,
          reorderLevel,
          currentUser.requireUserId());
      ra.addFlashAttribute("successKey", "pharmacy.stock.saved");
    } catch (IllegalArgumentException | PharmacyException e) {
      ra.addFlashAttribute(
          "errorMessage", e instanceof PharmacyException pe ? pe.messageKey() : e.getMessage());
    }
    return "redirect:/pharmacy/stock";
  }
}
