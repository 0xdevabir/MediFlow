package com.mediflow.clinical.consultation.service;

import com.mediflow.clinical.consultation.domain.AllergyConfirmEntity;
import com.mediflow.clinical.consultation.domain.Icd10Entity;
import com.mediflow.clinical.consultation.port.ClinicalAccess;
import com.mediflow.clinical.consultation.port.LabOrderPort;
import com.mediflow.clinical.consultation.repo.AllergyConfirmRepository;
import com.mediflow.clinical.consultation.repo.ClinicalAllergyRepository;
import com.mediflow.clinical.consultation.repo.ClinicalAppointmentRepository;
import com.mediflow.clinical.consultation.repo.ClinicalConditionRepository;
import com.mediflow.clinical.consultation.repo.ClinicalDiagnosticReportRepository;
import com.mediflow.clinical.consultation.repo.ClinicalEncounterRepository;
import com.mediflow.clinical.consultation.repo.ClinicalLabOrderRepository;
import com.mediflow.clinical.consultation.repo.ClinicalNoteQueryRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPractitionerRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPrescriptionItemRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPrescriptionRepository;
import com.mediflow.clinical.consultation.repo.Icd10Repository;
import com.mediflow.clinical.vitals.repo.ClinicalVitalObservationRepository;
import com.mediflow.shared.domain.Allergy;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.ClinicalNote;
import com.mediflow.shared.domain.ConditionDx;
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.DrugGeneric;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.EncounterStatus;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.VitalObservation;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.DrugGenericRepository;
import com.mediflow.shared.repo.PatientRepository;
import com.mediflow.shared.web.CurrentUser;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultationService {

  private static final Set<AppointmentStatus> CONSULT_READY =
      EnumSet.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS);

  private final ClinicalEncounterRepository encounters;
  private final ClinicalAppointmentRepository appointments;
  private final ClinicalPractitionerRepository practitioners;
  private final PatientRepository patients;
  private final ClinicalAllergyRepository allergies;
  private final AllergyConfirmRepository allergyConfirms;
  private final ClinicalNoteQueryRepository notes;
  private final ClinicalConditionRepository conditions;
  private final Icd10Repository icd10;
  private final ClinicalPrescriptionRepository prescriptions;
  private final ClinicalPrescriptionItemRepository prescriptionItems;
  private final DrugGenericRepository generics;
  private final ClinicalLabOrderRepository labOrders;
  private final ClinicalDiagnosticReportRepository reports;
  private final ClinicalVitalObservationRepository observations;
  private final LabOrderPort labOrdersPort;
  private final ClinicalAccess access;
  private final LabReportBadgeListener badges;
  private final AuditPort audit;
  private final ClockPort clock;
  private final CurrentUser currentUser;

  public ConsultationService(
      ClinicalEncounterRepository encounters,
      ClinicalAppointmentRepository appointments,
      ClinicalPractitionerRepository practitioners,
      PatientRepository patients,
      ClinicalAllergyRepository allergies,
      AllergyConfirmRepository allergyConfirms,
      ClinicalNoteQueryRepository notes,
      ClinicalConditionRepository conditions,
      Icd10Repository icd10,
      ClinicalPrescriptionRepository prescriptions,
      ClinicalPrescriptionItemRepository prescriptionItems,
      DrugGenericRepository generics,
      ClinicalLabOrderRepository labOrders,
      ClinicalDiagnosticReportRepository reports,
      ClinicalVitalObservationRepository observations,
      LabOrderPort labOrdersPort,
      ClinicalAccess access,
      LabReportBadgeListener badges,
      AuditPort audit,
      ClockPort clock,
      CurrentUser currentUser) {
    this.encounters = encounters;
    this.appointments = appointments;
    this.practitioners = practitioners;
    this.patients = patients;
    this.allergies = allergies;
    this.allergyConfirms = allergyConfirms;
    this.notes = notes;
    this.conditions = conditions;
    this.icd10 = icd10;
    this.prescriptions = prescriptions;
    this.prescriptionItems = prescriptionItems;
    this.generics = generics;
    this.labOrders = labOrders;
    this.reports = reports;
    this.observations = observations;
    this.labOrdersPort = labOrdersPort;
    this.access = access;
    this.badges = badges;
    this.audit = audit;
    this.clock = clock;
    this.currentUser = currentUser;
  }

  @PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN','ER')")
  @Transactional(readOnly = true)
  public List<Encounter> listOpenForCurrentDoctor() {
    Optional<Practitioner> me = currentPractitioner();
    if (me.isEmpty()) {
      if (currentUser.hasRole("ADMIN") || currentUser.hasRole("NURSE")) {
        return encounters.findAll().stream()
            .filter(e -> e.getStatus() == EncounterStatus.IN_PROGRESS)
            .limit(50)
            .toList();
      }
      return List.of();
    }
    return encounters.findByDoctorIdAndStatusOrderByStartedAtDesc(
        me.get().getId(), EncounterStatus.IN_PROGRESS);
  }

  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','ER')")
  @Transactional
  public Encounter openOrCreateFromAppointment(UUID appointmentId) {
    Appointment appt =
        appointments
            .findById(appointmentId)
            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
    if (!CONSULT_READY.contains(appt.getStatus())) {
      throw new IllegalStateException("Patient is not checked in");
    }
    requireConsult(appt.getPatientId());
    Optional<Encounter> existing = encounters.findByAppointmentId(appointmentId);
    if (existing.isPresent()) {
      return existing.get();
    }
    Practitioner me =
        currentPractitioner()
            .orElseThrow(() -> new AccessDeniedException("No practitioner linked to user"));
    if (appt.getDoctorId() != null && !appt.getDoctorId().equals(me.getId())
        && !currentUser.hasRole("ADMIN")) {
      throw new AccessDeniedException("Appointment is assigned to another doctor");
    }
    Encounter enc = new Encounter();
    enc.setPatientId(appt.getPatientId());
    enc.setDoctorId(me.getId());
    enc.setHospitalId(appt.getHospitalId());
    enc.setAppointmentId(appt.getId());
    enc.setStatus(EncounterStatus.IN_PROGRESS);
    enc.setStartedAt(clock.now());
    Encounter saved = encounters.save(enc);
    audit.record(
        currentUser.requireUserId(),
        enc.getPatientId(),
        "CONSULTATION_OPENED",
        "Encounter",
        saved.getId(),
        Map.of("appointmentId", appointmentId.toString()));
    return saved;
  }

  @PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN','ER')")
  @Transactional(readOnly = true)
  public ConsultationView load(UUID encounterId) {
    Encounter enc = requireEncounter(encounterId);
    requireRead(enc.getPatientId());
    Patient patient =
        patients
            .findById(enc.getPatientId())
            .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

    List<Allergy> allergyList = allergies.findByPatientIdOrderByCreatedAtDesc(patient.getId());
    Set<UUID> confirmedIds =
        allergyConfirms.findByPatientId(patient.getId()).stream()
            .map(AllergyConfirmEntity::getAllergyId)
            .collect(Collectors.toSet());
    List<AllergyView> allergyViews =
        allergyList.stream()
            .map(a -> new AllergyView(a, confirmedIds.contains(a.getId())))
            .toList();

    Optional<ClinicalNote> latest = notes.findFirstByEncounterIdOrderByNoteVersionDesc(enc.getId());
    List<ConditionDx> dx = conditions.findByEncounterIdOrderByRecordedAtDesc(enc.getId());
    List<ActiveMed> meds = activeMeds(patient.getId());
    List<LabReportView> lab = labReports(patient.getId());
    List<VitalObservation> vitals =
        observations.findByPatientIdOrderByRecordedAtDesc(patient.getId()).stream()
            .limit(200)
            .toList();

    currentUser
        .userId()
        .ifPresent(uid -> badges.markReadForPatient(uid, patient.getId()));

    audit.record(
        currentUser.userId().orElse(null),
        patient.getId(),
        "CONSULTATION_VIEW",
        "Encounter",
        enc.getId(),
        Map.of());

    return new ConsultationView(enc, patient, allergyViews, latest.orElse(null), dx, meds, lab, vitals);
  }

  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
  @Transactional
  public ClinicalNote saveNote(UUID encounterId, String content) {
    Encounter enc = requireEncounter(encounterId);
    requireConsult(enc.getPatientId());
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Note content required");
    }
    Optional<ClinicalNote> latest = notes.findFirstByEncounterIdOrderByNoteVersionDesc(enc.getId());
    ClinicalNote note = new ClinicalNote();
    note.setEncounterId(enc.getId());
    note.setContent(content.trim());
    note.setAuthorId(currentUser.requireUserId());
    if (latest.isPresent()) {
      note.setNoteVersion(latest.get().getNoteVersion() + 1);
      note.setSupersedesId(latest.get().getId());
    } else {
      note.setNoteVersion(1);
    }
    ClinicalNote saved = notes.save(note);
    audit.record(
        currentUser.requireUserId(),
        enc.getPatientId(),
        "CLINICAL_NOTE_SAVED",
        "ClinicalNote",
        saved.getId(),
        Map.of("noteVersion", saved.getNoteVersion()));
    return saved;
  }

  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
  @Transactional
  public ConditionDx addDiagnosis(UUID encounterId, String icd10Code) {
    Encounter enc = requireEncounter(encounterId);
    requireConsult(enc.getPatientId());
    Icd10Entity code =
        icd10
            .findById(icd10Code)
            .orElseThrow(() -> new IllegalArgumentException("Unknown ICD-10 code: " + icd10Code));
    ConditionDx dx = new ConditionDx();
    dx.setEncounterId(enc.getId());
    dx.setPatientId(enc.getPatientId());
    dx.setIcd10Code(code.getCode());
    dx.setDisplay(code.getDisplayEn());
    dx.setClinicalStatus("ACTIVE");
    dx.setRecordedBy(currentUser.requireUserId());
    dx.setRecordedAt(clock.now());
    ConditionDx saved = conditions.save(dx);
    enc.setDiagnosisSummary(summarizeDiagnoses(enc.getId()));
    encounters.save(enc);
    audit.record(
        currentUser.requireUserId(),
        enc.getPatientId(),
        "DIAGNOSIS_ADDED",
        "ConditionDx",
        saved.getId(),
        Map.of("icd10", code.getCode()));
    return saved;
  }

  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
  @Transactional
  public AllergyConfirmEntity confirmAllergy(UUID allergyId) {
    Allergy allergy =
        allergies
            .findById(allergyId)
            .orElseThrow(() -> new IllegalArgumentException("Allergy not found"));
    requireConsult(allergy.getPatientId());
    Optional<AllergyConfirmEntity> existing = allergyConfirms.findByAllergyId(allergyId);
    if (existing.isPresent()) {
      return existing.get();
    }
    AllergyConfirmEntity row = new AllergyConfirmEntity();
    row.setAllergyId(allergyId);
    row.setPatientId(allergy.getPatientId());
    row.setConfirmedBy(currentUser.requireUserId());
    row.setConfirmedAt(clock.now());
    AllergyConfirmEntity saved = allergyConfirms.save(row);
    audit.record(
        currentUser.requireUserId(),
        allergy.getPatientId(),
        "ALLERGY_CONFIRMED",
        "Allergy",
        allergyId,
        Map.of());
    return saved;
  }

  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
  @Transactional
  public UUID orderLab(UUID encounterId, String testCode) {
    Encounter enc = requireEncounter(encounterId);
    if (!access.canOrderLab(enc.getPatientId())) {
      throw new AccessDeniedException("Cannot order lab");
    }
    if (testCode == null || testCode.isBlank()) {
      throw new IllegalArgumentException("testCode required");
    }
    UUID orderId =
        labOrdersPort.requestOrder(
            enc.getId(), enc.getPatientId(), enc.getHospitalId(), testCode.trim());
    audit.record(
        currentUser.requireUserId(),
        enc.getPatientId(),
        "LAB_ORDER_REQUESTED",
        "Encounter",
        enc.getId(),
        Map.of("testCode", testCode.trim(), "orderId", orderId.toString()));
    return orderId;
  }

  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','NURSE')")
  @Transactional(readOnly = true)
  public String patientSummary(UUID encounterId) {
    ConsultationView view = load(encounterId);
    StringBuilder sb = new StringBuilder();
    sb.append("Patient: ")
        .append(view.patient().getGivenName())
        .append(' ')
        .append(view.patient().getFamilyName())
        .append(" (MRN ")
        .append(Objects.toString(view.patient().getMrn(), "—"))
        .append(")\n");
    sb.append("Diagnoses:\n");
    if (view.diagnoses().isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (ConditionDx d : view.diagnoses()) {
        sb.append("  - ").append(d.getIcd10Code()).append(' ').append(d.getDisplay()).append('\n');
      }
    }
    sb.append("Allergies:\n");
    for (AllergyView a : view.allergies()) {
      sb.append("  - ")
          .append(a.allergy().getSubstance())
          .append(a.confirmed() ? " (confirmed)" : " (unverified)")
          .append('\n');
    }
    sb.append("Active medicines:\n");
    if (view.activeMeds().isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (ActiveMed m : view.activeMeds()) {
        sb.append("  - ")
            .append(m.genericName())
            .append(' ')
            .append(Objects.toString(m.dose(), ""))
            .append(' ')
            .append(Objects.toString(m.frequency(), ""))
            .append('\n');
      }
    }
    if (view.latestNote() != null) {
      sb.append("Doctor notes (v")
          .append(view.latestNote().getNoteVersion())
          .append("):\n")
          .append(view.latestNote().getContent())
          .append('\n');
    }
    return sb.toString();
  }

  public long unreadLabBadgeCount() {
    return currentUser.userId().map(badges::unreadCount).orElse(0L);
  }

  private List<ActiveMed> activeMeds(UUID patientId) {
    List<Prescription> signed =
        prescriptions.findByPatientIdAndStatusInOrderBySignedAtDesc(
            patientId, EnumSet.of(PrescriptionStatus.SIGNED, PrescriptionStatus.AMENDED));
    if (signed.isEmpty()) {
      return List.of();
    }
    Set<UUID> rxIds = signed.stream().map(Prescription::getId).collect(Collectors.toSet());
    List<PrescriptionItem> items = prescriptionItems.findByPrescriptionIdIn(rxIds);
    Set<UUID> genericIds =
        items.stream().map(PrescriptionItem::getGenericId).collect(Collectors.toSet());
    Map<UUID, String> names = new HashMap<>();
    for (UUID gid : genericIds) {
      generics.findById(gid).map(DrugGeneric::getName).ifPresent(n -> names.put(gid, n));
    }
    List<ActiveMed> result = new ArrayList<>();
    Set<UUID> seenGenerics = new HashSet<>();
    for (PrescriptionItem item : items) {
      if (!seenGenerics.add(item.getGenericId())) {
        continue;
      }
      result.add(
          new ActiveMed(
              names.getOrDefault(item.getGenericId(), item.getGenericId().toString()),
              item.getDose(),
              item.getFrequency(),
              item.getRoute()));
    }
    return result;
  }

  private List<LabReportView> labReports(UUID patientId) {
    List<LabOrder> orders = labOrders.findByPatientIdOrderByCreatedAtDesc(patientId);
    if (orders.isEmpty()) {
      return List.of();
    }
    Map<UUID, LabOrder> byId =
        orders.stream().collect(Collectors.toMap(LabOrder::getId, o -> o, (a, b) -> a));
    List<DiagnosticReport> reps =
        reports.findByLabOrderIdIn(byId.keySet()).stream()
            .filter(r -> r.getVerifiedAt() != null)
            .toList();
    List<LabReportView> views = new ArrayList<>();
    for (DiagnosticReport r : reps) {
      LabOrder o = byId.get(r.getLabOrderId());
      views.add(new LabReportView(r, o));
    }
    return views;
  }

  private String summarizeDiagnoses(UUID encounterId) {
    return conditions.findByEncounterIdOrderByRecordedAtDesc(encounterId).stream()
        .map(d -> d.getIcd10Code() + " " + d.getDisplay())
        .collect(Collectors.joining("; "));
  }

  private void requireConsult(UUID patientId) {
    if (!access.canConsult(patientId)) {
      throw new AccessDeniedException("Not assigned to patient " + patientId);
    }
  }

  private void requireRead(UUID patientId) {
    if (!access.canReadPatient(patientId)) {
      throw new AccessDeniedException("Cannot read patient " + patientId);
    }
  }

  private Encounter requireEncounter(UUID id) {
    return encounters
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Encounter not found: " + id));
  }

  private Optional<Practitioner> currentPractitioner() {
    return currentUser.userId().flatMap(practitioners::findByUserId);
  }

  public record AllergyView(Allergy allergy, boolean confirmed) {}

  public record ActiveMed(String genericName, String dose, String frequency, String route) {}

  public record LabReportView(DiagnosticReport report, LabOrder order) {}

  public record ConsultationView(
      Encounter encounter,
      Patient patient,
      List<AllergyView> allergies,
      ClinicalNote latestNote,
      List<ConditionDx> diagnoses,
      List<ActiveMed> activeMeds,
      List<LabReportView> labReports,
      List<VitalObservation> vitals) {}
}
