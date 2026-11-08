ConsultationService.java 490L cognitive
// /Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/V2-06-clinical-consultation/backend/src/main/java/com/mediflow/clinical/consultation/service/ConsultationService.java
§ block block (L1-L56)
package com.mediflow.clinical.consultation.service;

import com.mediflow.clinical.consultation.domain.AllergyConfirmEntity;
import com.mediflow.clinical.consultation.domain.Icd10Entity;
import com.mediflow.clinical.consultation.port.ClinicalAccess;
import com.mediflow.shared.port.LabOrderPort;
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

§ type ConsultationService (L57-L84)
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

§ function function (L85-L128)
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
// ... 1 lines omitted
§ function function (L130-L145)
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
4/20 chunks shown (1306 tokens)
[lean-ctx] full source: read "/Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/V2-06-clinical-consultation/backend/src/main/java/com/mediflow/clinical/consultation/service/ConsultationService.java" directly (no MCP)  ·  or ctx_read("/Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/V2-06-clinical-consultation/backend/src/main/java/com/mediflow/clinical/consultation/service/ConsultationService.java", mode="full")
