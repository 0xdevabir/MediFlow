package com.mediflow.appointment.web;

import com.mediflow.appointment.booking.BookingMeta;
import com.mediflow.appointment.booking.BookingService;
import com.mediflow.appointment.booking.FreeSlot;
import com.mediflow.appointment.booking.SlotCatalog;
import com.mediflow.appointment.queue.CheckinService;
import com.mediflow.appointment.support.ApptPatientRepository;
import com.mediflow.appointment.support.QueueClock;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
@RequestMapping("/appointments")
public class AppointmentController {

  private final BookingService booking;
  private final SlotCatalog slots;
  private final CheckinService checkin;
  private final ApptPatientRepository patients;
  private final CurrentUser currentUser;
  private final ClockPort clock;

  public AppointmentController(
      BookingService booking,
      SlotCatalog slots,
      CheckinService checkin,
      ApptPatientRepository patients,
      CurrentUser currentUser,
      ClockPort clock) {
    this.booking = booking;
    this.slots = slots;
    this.checkin = checkin;
    this.patients = patients;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @GetMapping({"", "/", "/home"})
  public String index(Model model) {
    if (currentUser.hasRole("PATIENT")) {
      model.addAttribute("appointments", booking.myAppointments());
      return "appointment/list";
    }
    if (currentUser.hasRole("FRONT_DESK") || currentUser.hasRole("ADMIN")) {
      return "redirect:/appointments/check-in";
    }
    if (currentUser.hasRole("NURSE")) {
      return "redirect:/queue/home";
    }
    if (currentUser.hasRole("DOCTOR")) {
      return "redirect:/queue/doctor";
    }
    return "appointment/list";
  }

  @GetMapping("/book")
  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK')")
  public String bookForm(
      @RequestParam(required = false) UUID hospitalId,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) UUID patientId,
      Model model) {
    Instant from = clock.now();
    Instant to = from.plus(7, ChronoUnit.DAYS);
    List<FreeSlot> free = slots.freeSlots(hospitalId, doctorId, from, to);
    model.addAttribute("slots", free);
    model.addAttribute("hospitalId", hospitalId);
    model.addAttribute("doctorId", doctorId);
    model.addAttribute("patientId", patientId);
    model.addAttribute("onBehalf", currentUser.hasRole("FRONT_DESK"));
    return "appointment/book";
  }

  @PostMapping("/book")
  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK')")
  public String book(
      @RequestParam UUID slotId,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(defaultValue = "false") boolean emergency,
      @RequestParam(required = false) String emergencyNote,
      RedirectAttributes ra) {
    Appointment appt;
    if (currentUser.hasRole("FRONT_DESK")) {
      if (patientId == null) {
        ra.addFlashAttribute("errorKey", "appointment.error.patientRequired");
        return "redirect:/appointments/book";
      }
      appt = booking.bookOnBehalf(patientId, slotId, emergency, emergencyNote);
    } else {
      appt = booking.bookForCurrentPatient(slotId);
    }
    ra.addFlashAttribute("successKey", "appointment.flash.booked");
    return "redirect:/appointments/" + appt.getId();
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public String detail(@PathVariable UUID id, Model model) {
    Appointment appt = booking.get(id);
    Optional<BookingMeta> meta = booking.meta(id);
    model.addAttribute("appointment", appt);
    model.addAttribute("meta", meta.orElse(null));
    model.addAttribute(
        "slot", appt.getSlotId() == null ? null : slots.find(appt.getSlotId()).orElse(null));
    patients.findById(appt.getPatientId()).ifPresent(p -> model.addAttribute("patient", p));
    return "appointment/detail";
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK','ADMIN')")
  public String cancel(@PathVariable UUID id, RedirectAttributes ra) {
    booking.cancel(id);
    ra.addFlashAttribute("successKey", "appointment.flash.cancelled");
    return "redirect:/appointments/home";
  }

  @GetMapping("/{id}/reschedule")
  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK','ADMIN')")
  public String rescheduleForm(@PathVariable UUID id, Model model) {
    Appointment appt = booking.get(id);
    Instant from = clock.now();
    List<FreeSlot> free =
        slots.freeSlots(
            appt.getHospitalId(), appt.getDoctorId(), from, from.plus(14, ChronoUnit.DAYS));
    model.addAttribute("appointment", appt);
    model.addAttribute("slots", free);
    return "appointment/reschedule";
  }

  @PostMapping("/{id}/reschedule")
  @PreAuthorize("hasAnyRole('PATIENT','FRONT_DESK','ADMIN')")
  public String reschedule(
      @PathVariable UUID id, @RequestParam UUID slotId, RedirectAttributes ra) {
    Appointment appt = booking.reschedule(id, slotId);
    ra.addFlashAttribute("successKey", "appointment.flash.rescheduled");
    return "redirect:/appointments/" + appt.getId();
  }

  @PostMapping("/{id}/flag-emergency")
  @PreAuthorize("hasAnyRole('FRONT_DESK','NURSE')")
  public String flagEmergency(
      @PathVariable UUID id, @RequestParam(required = false) String note, RedirectAttributes ra) {
    booking.flagEmergency(id, note);
    ra.addFlashAttribute("successKey", "appointment.flash.emergencyFlagged");
    return "redirect:/appointments/" + id;
  }

  @GetMapping("/check-in")
  @PreAuthorize("hasRole('FRONT_DESK')")
  public String checkinForm(Model model) {
    model.addAttribute("today", QueueClock.queueDate(clock.now()));
    return "appointment/checkin";
  }

  @PostMapping("/check-in/code")
  @PreAuthorize("hasRole('FRONT_DESK')")
  public String checkinCode(@RequestParam String code, RedirectAttributes ra) {
    var result = checkin.checkInByCode(code);
    ra.addFlashAttribute("successKey", "appointment.flash.checkedIn");
    ra.addFlashAttribute("tokenNo", result.queueEntry().getTokenNo());
    return "redirect:/appointments/" + result.appointment().getId();
  }

  @PostMapping("/check-in/identifiers")
  @PreAuthorize("hasRole('FRONT_DESK')")
  public String checkinIdentifiers(
      @RequestParam UUID appointmentId,
      @RequestParam String identifierA,
      @RequestParam String identifierB,
      RedirectAttributes ra) {
    var result = checkin.checkInWithIdentifiers(appointmentId, identifierA, identifierB);
    ra.addFlashAttribute("successKey", "appointment.flash.checkedIn");
    ra.addFlashAttribute("tokenNo", result.queueEntry().getTokenNo());
    return "redirect:/appointments/" + result.appointment().getId();
  }

  @GetMapping("/patient-lookup")
  @PreAuthorize("hasRole('FRONT_DESK')")
  public String patientLookup(@RequestParam String mrn, Model model) {
    Optional<Patient> patient = patients.findByMrn(mrn.trim());
    model.addAttribute("mrn", mrn);
    model.addAttribute("patient", patient.orElse(null));
    if (patient.isPresent()) {
      model.addAttribute("appointments", booking.appointmentsForPatient(patient.get().getId()));
    }
    return "appointment/checkin";
  }
}
