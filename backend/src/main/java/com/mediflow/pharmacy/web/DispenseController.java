package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.dispensing.DispensingService;
import com.mediflow.pharmacy.dispensing.DispensingService.RxView;
import com.mediflow.pharmacy.dispensing.PharmacyException;
import com.mediflow.pharmacy.dispensing.PharmacyHospitalResolver;
import com.mediflow.pharmacy.dispensing.PharmacyRequestService;
import com.mediflow.pharmacy.stock.StockService;
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

@Controller
@RequestMapping("/pharmacy/rx")
@PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
public class DispenseController {

  private final DispensingService dispensing;
  private final PharmacyRequestService requests;
  private final StockService stock;
  private final PharmacyHospitalResolver hospitals;
  private final CurrentUser currentUser;

  public DispenseController(
      DispensingService dispensing,
      PharmacyRequestService requests,
      StockService stock,
      PharmacyHospitalResolver hospitals,
      CurrentUser currentUser) {
    this.dispensing = dispensing;
    this.requests = requests;
    this.stock = stock;
    this.hospitals = hospitals;
    this.currentUser = currentUser;
  }

  @GetMapping("/{id}")
  public String view(@PathVariable UUID id, Model model, RedirectAttributes ra) {
    try {
      UUID hospitalId = hospitals.requireHospital(currentUser.requireUserId());
      RxView view = dispensing.loadForDispense(id, hospitalId);
      model.addAttribute("pageTitleKey", "pharmacy.dispense.title");
      model.addAttribute("view", view);
      model.addAttribute("hospitalId", hospitalId);
      return "pharmacy/dispense";
    } catch (PharmacyException e) {
      ra.addFlashAttribute("errorKey", e.messageKey());
      return "redirect:/pharmacy";
    }
  }

  @PostMapping("/{id}/dispense")
  public String dispense(
      @PathVariable UUID id,
      @RequestParam UUID itemId,
      @RequestParam UUID batchId,
      @RequestParam int quantity,
      @RequestParam(required = false) UUID brandId,
      @RequestParam(required = false) UUID substitutionRequestId,
      @RequestParam(required = false) String qrToken,
      @RequestParam(required = false) String idA,
      @RequestParam(required = false) String idB,
      RedirectAttributes ra) {
    try {
      dispensing.dispense(
          id,
          itemId,
          batchId,
          quantity,
          brandId,
          substitutionRequestId,
          qrToken,
          idA,
          idB,
          currentUser.requireUserId());
      ra.addFlashAttribute("successKey", "pharmacy.dispense.done");
    } catch (PharmacyException e) {
      ra.addFlashAttribute("errorKey", e.messageKey());
    }
    return "redirect:/pharmacy/rx/" + id;
  }

  @PostMapping("/{id}/send-back")
  public String sendBack(
      @PathVariable UUID id, @RequestParam String reason, RedirectAttributes ra) {
    try {
      requests.sendBack(id, reason, currentUser.requireUserId());
      ra.addFlashAttribute("successKey", "pharmacy.request.sendBackDone");
      return "redirect:/pharmacy";
    } catch (PharmacyException e) {
      ra.addFlashAttribute("errorKey", e.messageKey());
      return "redirect:/pharmacy/rx/" + id;
    }
  }

  @PostMapping("/{id}/substitute")
  public String substitute(
      @PathVariable UUID id,
      @RequestParam UUID itemId,
      @RequestParam UUID proposedBrandId,
      @RequestParam String reason,
      RedirectAttributes ra) {
    try {
      requests.requestSubstitution(
          id, itemId, proposedBrandId, reason, currentUser.requireUserId());
      ra.addFlashAttribute("successKey", "pharmacy.request.substitutionPending");
    } catch (PharmacyException e) {
      ra.addFlashAttribute("errorKey", e.messageKey());
    }
    return "redirect:/pharmacy/rx/" + id;
  }

  @PostMapping("/{id}/refer")
  public String refer(@PathVariable UUID id, @RequestParam String reason, RedirectAttributes ra) {
    try {
      requests.referElsewhere(id, reason, currentUser.requireUserId());
      ra.addFlashAttribute("successKey", "pharmacy.request.referred");
      return "redirect:/pharmacy";
    } catch (PharmacyException e) {
      ra.addFlashAttribute("errorKey", e.messageKey());
      return "redirect:/pharmacy/rx/" + id;
    }
  }

  @GetMapping("/{id}/batches")
  public String batches(
      @PathVariable UUID id,
      @RequestParam UUID brandId,
      Model model,
      RedirectAttributes ra) {
    try {
      UUID hospitalId = hospitals.requireHospital(currentUser.requireUserId());
      UUID stockId =
          stock.list(hospitalId).stream()
              .filter(s -> s.brandId().equals(brandId))
              .map(s -> s.stockId())
              .findFirst()
              .orElse(null);
      model.addAttribute("pageTitleKey", "pharmacy.stock.batches");
      model.addAttribute("rxId", id);
      model.addAttribute("brandId", brandId);
      model.addAttribute("batches", stockId == null ? java.util.List.of() : stock.batches(stockId));
      return "pharmacy/batches";
    } catch (PharmacyException e) {
      ra.addFlashAttribute("errorKey", e.messageKey());
      return "redirect:/pharmacy/rx/" + id;
    }
  }
}
