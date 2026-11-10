package com.mediflow.shared.web;

import com.mediflow.appointment.support.AppointmentAccess;
import com.mediflow.clinical.consultation.service.ConsultationService;
import com.mediflow.patient.service.PatientAppointmentGlancePort;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.port.AdherenceQueryPort;
import com.mediflow.shared.repo.PatientRepository;
import com.mediflow.shared.repo.PractitionerRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON endpoints backing the Next.js frontend (frontend/). Session-cookie auth, same as the
 * Thymeleaf pages; the Next server proxies /api/** here so the cookie stays same-origin.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("isAuthenticated()")
public class SpaApiController {

  public record Me(
      UUID id, String username, String displayName, String locale, List<String> roles) {}

  public record EncounterCard(
      UUID id, UUID patientId, String patientName, String mrn, String status, Instant startedAt) {}

  public record NextVisit(UUID appointmentId, Instant when, String summary) {}

  public record Dashboard(
      List<String> roles,
      UUID patientId,
      NextVisit nextVisit,
      Integer adherencePercent,
      List<EncounterCard> openToday,
      List<EncounterCard> vitalsPending,
      long labBadgeCount) {}

  public record Doctor(UUID id, String name, String roleLabel) {}

  public record QueueContext(UUID myDoctorId, List<Doctor> doctors) {}

  private final CurrentUser currentUser;
  private final PatientRepository patients;
  private final PractitionerRepository practitioners;
  private final AppointmentAccess appointmentAccess;
  private final ObjectProvider<AdherenceQueryPort> adherence;
  private final ObjectProvider<PatientAppointmentGlancePort> appointmentGlance;
  private final ObjectProvider<ConsultationService> consultations;

  public SpaApiController(
      CurrentUser currentUser,
      PatientRepository patients,
      PractitionerRepository practitioners,
      AppointmentAccess appointmentAccess,
      ObjectProvider<AdherenceQueryPort> adherence,
      ObjectProvider<PatientAppointmentGlancePort> appointmentGlance,
      ObjectProvider<ConsultationService> consultations) {
    this.currentUser = currentUser;
    this.patients = patients;
    this.practitioners = practitioners;
    this.appointmentAccess = appointmentAccess;
    this.adherence = adherence;
    this.appointmentGlance = appointmentGlance;
    this.consultations = consultations;
  }

  @GetMapping("/me")
  public Me me() {
    var user = currentUser.appUser();
    return new Me(
        user.map(u -> u.getId()).orElse(null),
        currentUser.username().orElse(""),
        user.map(u -> u.getDisplayName()).orElse(currentUser.username().orElse("")),
        user.map(u -> u.getLocale()).orElse("en"),
        roles());
  }

  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  public Dashboard dashboard() {
    UUID patientId = null;
    NextVisit nextVisit = null;
    Integer adherencePct = null;
    if (currentUser.hasRole("PATIENT")) {
      patientId =
          currentUser.userId().flatMap(patients::findByUserId).map(Patient::getId).orElse(null);
      if (patientId != null) {
        AdherenceQueryPort port = adherence.getIfAvailable();
        if (port != null) {
          adherencePct = port.adherencePercent(patientId).orElse(null);
        }
        PatientAppointmentGlancePort glance = appointmentGlance.getIfAvailable();
        if (glance != null) {
          nextVisit =
              glance
                  .nextUpcoming(patientId)
                  .map(v -> new NextVisit(v.appointmentId(), v.when(), v.summary()))
                  .orElse(null);
        }
      }
    }

    List<EncounterCard> openToday = List.of();
    List<EncounterCard> vitalsPending = List.of();
    long labBadges = 0;
    ConsultationService clinical = consultations.getIfAvailable();
    boolean clinicalRole =
        currentUser.hasRole("DOCTOR")
            || currentUser.hasRole("NURSE")
            || currentUser.hasRole("ADMIN")
            || currentUser.hasRole("ER");
    if (clinical != null && clinicalRole) {
      if (currentUser.hasRole("DOCTOR") || currentUser.hasRole("ADMIN")) {
        openToday = cards(clinical.listOpenTodayForCurrentDoctor());
      }
      vitalsPending = cards(clinical.listVitalsPending(10));
      labBadges = clinical.unreadLabBadgeCount();
    }
    return new Dashboard(
        roles(), patientId, nextVisit, adherencePct, openToday, vitalsPending, labBadges);
  }

  @GetMapping("/queue/context")
  @PreAuthorize("hasAnyRole('FRONT_DESK','NURSE','DOCTOR','ADMIN')")
  @Transactional(readOnly = true)
  public QueueContext queueContext() {
    UUID mine =
        currentUser.hasRole("DOCTOR")
            ? appointmentAccess.currentPractitioner().map(Practitioner::getId).orElse(null)
            : null;
    List<Doctor> doctors =
        practitioners.findAll().stream()
            .map(p -> new Doctor(p.getId(), p.getName(), p.getRoleLabel()))
            .sorted(Comparator.comparing(Doctor::name, Comparator.nullsLast(String::compareTo)))
            .toList();
    return new QueueContext(mine, doctors);
  }

  private List<EncounterCard> cards(List<Encounter> encounters) {
    if (encounters.isEmpty()) {
      return List.of();
    }
    Map<UUID, Patient> byId =
        patients
            .findAllById(encounters.stream().map(Encounter::getPatientId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Patient::getId, Function.identity()));
    return encounters.stream()
        .map(
            e -> {
              Optional<Patient> p = Optional.ofNullable(byId.get(e.getPatientId()));
              return new EncounterCard(
                  e.getId(),
                  e.getPatientId(),
                  p.map(x -> (x.getGivenName() + " " + x.getFamilyName()).trim()).orElse("—"),
                  p.map(Patient::getMrn).orElse(null),
                  e.getStatus().name(),
                  e.getStartedAt());
            })
        .toList();
  }

  private static List<String> roles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return List.of();
    }
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a.startsWith("ROLE_"))
        .map(a -> a.substring(5))
        .sorted()
        .toList();
  }
}
