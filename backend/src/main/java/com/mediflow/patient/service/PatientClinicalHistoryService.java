package com.mediflow.patient.service;

import com.mediflow.patient.domain.ClinicalSource;
import com.mediflow.patient.domain.HistoryCategory;
import com.mediflow.patient.domain.PatientAllergy;
import com.mediflow.patient.domain.PatientHistoryItem;
import com.mediflow.patient.repo.PatientAllergyRepository;
import com.mediflow.patient.repo.PatientHistoryItemRepository;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-reported allergies & history ({@code verified=false}) plus doctor confirm for T05.
 */
@Service
public class PatientClinicalHistoryService {

  private final PatientLookupRepository patients;
  private final PatientAllergyRepository allergies;
  private final PatientHistoryItemRepository history;
  private final PatientAudit audit;
  private final CurrentUser currentUser;
  private final ClockPort clock;

  public PatientClinicalHistoryService(
      PatientLookupRepository patients,
      PatientAllergyRepository allergies,
      PatientHistoryItemRepository history,
      PatientAudit audit,
      CurrentUser currentUser,
      ClockPort clock) {
    this.patients = patients;
    this.allergies = allergies;
    this.history = history;
    this.audit = audit;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@patientAccess.canReadClinicalHistory(#patientId)")
  public List<PatientAllergy> listAllergies(UUID patientId) {
    requirePatient(patientId);
    audit.record(patientId, "ALLERGY_LIST", PatientAudit.ALLERGY, patientId);
    return allergies.findByPatientIdOrderByCreatedAtAsc(patientId);
  }

  @Transactional
  @PreAuthorize("@patientAccess.isSelf(#patientId) or @patientAccess.canConfirmClinicalHistory(#patientId)")
  public PatientAllergy addAllergy(
      UUID patientId, String substance, String substanceCode, String criticality, String note) {
    requirePatient(patientId);
    if (substance == null || substance.isBlank()) {
      throw new PatientRuleException("patient.allergy.substanceRequired");
    }
    boolean clinician = currentUser.hasRole("DOCTOR") || currentUser.hasRole("ER");
    PatientAllergy a = new PatientAllergy();
    a.setPatientId(patientId);
    a.setSubstance(substance.trim());
    a.setSubstanceCode(blankToNull(substanceCode));
    a.setCriticality(blankToNull(criticality));
    a.setNote(blankToNull(note));
    a.setSource(clinician ? ClinicalSource.CLINICIAN : ClinicalSource.SELF_REPORTED);
    a.setReportedBy(currentUser.userId().orElse(null));
    a.setVerified(clinician);
    if (clinician) {
      a.setVerifiedBy(currentUser.requireUserId());
      a.setVerifiedAt(clock.now());
    }
    allergies.save(a);
    audit.record(
        patientId,
        "ALLERGY_ADDED",
        PatientAudit.ALLERGY,
        a.getId(),
        Map.of("verified", a.isVerified(), "source", a.getSource().name()));
    return a;
  }

  /** Doctor confirm action exposed for T05. */
  @Transactional
  @PreAuthorize("@patientAccess.canConfirmClinicalHistory(#patientId)")
  public PatientAllergy confirmAllergy(UUID patientId, UUID allergyId) {
    PatientAllergy a =
        allergies
            .findById(allergyId)
            .filter(x -> x.getPatientId().equals(patientId))
            .orElseThrow(() -> new PatientRuleException("patient.allergy.notFound"));
    a.setVerified(true);
    a.setVerifiedBy(currentUser.requireUserId());
    a.setVerifiedAt(clock.now());
    a.setSource(ClinicalSource.CLINICIAN);
    allergies.save(a);
    audit.record(patientId, "ALLERGY_CONFIRMED", PatientAudit.ALLERGY, a.getId());
    return a;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@patientAccess.canReadClinicalHistory(#patientId)")
  public List<PatientHistoryItem> listHistory(UUID patientId) {
    requirePatient(patientId);
    audit.record(patientId, "HISTORY_LIST", PatientAudit.HISTORY, patientId);
    return history.findByPatientIdOrderByCreatedAtAsc(patientId);
  }

  @Transactional
  @PreAuthorize("@patientAccess.isSelf(#patientId) or @patientAccess.canConfirmClinicalHistory(#patientId)")
  public PatientHistoryItem addHistory(
      UUID patientId, HistoryCategory category, String description, String sinceText) {
    requirePatient(patientId);
    if (category == null || description == null || description.isBlank()) {
      throw new PatientRuleException("patient.history.incomplete");
    }
    boolean clinician = currentUser.hasRole("DOCTOR") || currentUser.hasRole("ER");
    PatientHistoryItem item = new PatientHistoryItem();
    item.setPatientId(patientId);
    item.setCategory(category);
    item.setDescription(description.trim());
    item.setSinceText(blankToNull(sinceText));
    item.setSource(clinician ? ClinicalSource.CLINICIAN : ClinicalSource.SELF_REPORTED);
    item.setReportedBy(currentUser.userId().orElse(null));
    item.setVerified(clinician);
    if (clinician) {
      item.setVerifiedBy(currentUser.requireUserId());
      item.setVerifiedAt(clock.now());
    }
    history.save(item);
    audit.record(
        patientId,
        "HISTORY_ADDED",
        PatientAudit.HISTORY,
        item.getId(),
        Map.of("verified", item.isVerified()));
    return item;
  }

  @Transactional
  @PreAuthorize("@patientAccess.canConfirmClinicalHistory(#patientId)")
  public PatientHistoryItem confirmHistory(UUID patientId, UUID historyId) {
    PatientHistoryItem item =
        history
            .findById(historyId)
            .filter(x -> x.getPatientId().equals(patientId))
            .orElseThrow(() -> new PatientRuleException("patient.history.notFound"));
    item.setVerified(true);
    item.setVerifiedBy(currentUser.requireUserId());
    item.setVerifiedAt(clock.now());
    item.setSource(ClinicalSource.CLINICIAN);
    history.save(item);
    audit.record(patientId, "HISTORY_CONFIRMED", PatientAudit.HISTORY, item.getId());
    return item;
  }

  private void requirePatient(UUID patientId) {
    patients.findById(patientId).orElseThrow(() -> new PatientRuleException("patient.notFound"));
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
