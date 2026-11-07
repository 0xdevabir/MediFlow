package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.dispensing.DispensingService;
import com.mediflow.pharmacy.dispensing.PharmacyHospitalResolver;
import com.mediflow.pharmacy.dispensing.PharmacyRequestService;
import com.mediflow.pharmacy.stock.StockService;
import com.mediflow.shared.web.CurrentUser;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pharmacy home. Takes over {@code GET /pharmacy} once T00 removes it from {@code
 * PlaceholderController} (see {@code docs/plan/requests/T08.md}).
 */
@Controller
@PreAuthorize("hasAnyRole('PHARMACIST','ADMIN','DOCTOR')")
public class PharmacyHomeController {

  private final DispensingService dispensing;
  private final StockService stock;
  private final PharmacyRequestService requests;
  private final PharmacyHospitalResolver hospitals;
  private final CurrentUser currentUser;

  public PharmacyHomeController(
      DispensingService dispensing,
      StockService stock,
      PharmacyRequestService requests,
      PharmacyHospitalResolver hospitals,
      CurrentUser currentUser) {
    this.dispensing = dispensing;
    this.stock = stock;
    this.requests = requests;
    this.hospitals = hospitals;
    this.currentUser = currentUser;
  }

  @GetMapping("/pharmacy")
  public String home(Model model) {
    model.addAttribute("pageTitleKey", "pharmacy.home.title");
    UUID userId = currentUser.requireUserId();
    if (currentUser.hasRole("PHARMACIST") || currentUser.hasRole("ADMIN")) {
      UUID hospitalId = hospitals.requireHospital(userId);
      model.addAttribute("hospitalId", hospitalId);
      model.addAttribute("queue", dispensing.queueSigned(hospitalId));
      model.addAttribute("lowStock", stock.lowStock(hospitalId));
    }
    if (currentUser.hasRole("DOCTOR") || currentUser.hasRole("ADMIN")) {
      model.addAttribute("pendingSubstitutions", requests.pendingForDoctor(userId));
    }
    return "pharmacy/home";
  }
}
