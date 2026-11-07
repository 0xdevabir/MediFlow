package com.mediflow.lab.web;

import com.mediflow.lab.service.LabCatalogService;
import com.mediflow.lab.service.LabOrderService;
import com.mediflow.lab.service.LabSlots;
import com.mediflow.lab.service.LabViews;
import com.mediflow.shared.web.CurrentUser;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/lab/direct")
public class LabDirectController {

  private final LabCatalogService catalog;
  private final LabOrderService orders;
  private final CurrentUser currentUser;

  public LabDirectController(
      LabCatalogService catalog, LabOrderService orders, CurrentUser currentUser) {
    this.catalog = catalog;
    this.orders = orders;
    this.currentUser = currentUser;
  }

  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK')")
  @GetMapping
  public String form(
      @RequestParam(required = false) UUID hospitalId,
      Model model) {
    model.addAttribute("hospitals", catalog.activeHospitals());
    model.addAttribute("hospitalId", hospitalId);
    model.addAttribute("catalog", catalog.directCatalog(hospitalId));
    model.addAttribute("slots", LabSlots.WINDOWS);
    model.addAttribute("today", LocalDate.now(LabSlots.CLINIC_ZONE));
    model.addAttribute("maxDate", LocalDate.now(LabSlots.CLINIC_ZONE).plusDays(LabSlots.MAX_DAYS_AHEAD));
    model.addAttribute("isFrontDesk", currentUser.hasRole("FRONT_DESK"));
    return "lab/direct";
  }

  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK')")
  @PostMapping
  public String book(
      @RequestParam UUID hospitalId,
      @RequestParam(required = false) List<UUID> testIds,
      @RequestParam(required = false) List<UUID> packageIds,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam String slot,
      @RequestParam(required = false) String mrn,
      RedirectAttributes flash) {
    LabViews.DirectBooking booking;
    if (currentUser.hasRole("FRONT_DESK")) {
      booking = orders.bookDirectForWalkIn(mrn, hospitalId, testIds, packageIds, date, slot);
    } else {
      booking = orders.bookDirectForSelf(hospitalId, testIds, packageIds, date, slot);
    }
    flash.addFlashAttribute("successKey", "lab.direct.booked");
    flash.addFlashAttribute("bookingRef", booking.bookingRef());
    flash.addFlashAttribute("bookingTotal", booking.total());
    if (currentUser.hasRole("PATIENT")) {
      return "redirect:/lab/reports";
    }
    return "redirect:/lab/direct";
  }
}
