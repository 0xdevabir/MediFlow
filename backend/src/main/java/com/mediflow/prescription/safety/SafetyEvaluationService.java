package com.mediflow.prescription.safety;

import com.mediflow.prescription.domain.RxItemMeta;
import com.mediflow.prescription.domain.RxSafetyFlag;
import com.mediflow.prescription.repo.RxClinicalQueries;
import com.mediflow.prescription.repo.RxEncounterQueries;
import com.mediflow.prescription.repo.RxItemMetaRepository;
import com.mediflow.prescription.repo.RxItemQueries;
import com.mediflow.prescription.repo.RxPrescriptionQueries;
import com.mediflow.prescription.repo.RxSafetyFlagRepository;
import com.mediflow.prescription.safety.PatientSafetyContext.ActiveMedRef;
import com.mediflow.prescription.safety.PatientSafetyContext.AllergyRef;
import com.mediflow.prescription.safety.PatientSafetyContext.ConditionRef;
import com.mediflow.prescription.safety.PatientSafetyContext.VitalRef;
import com.mediflow.prescription.safety.RxDrugDictionary.BrandInfo;
import com.mediflow.prescription.safety.RxDrugDictionary.GenericInfo;
import com.mediflow.shared.domain.ConflictSeverity;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.ReviewStatus;
import com.mediflow.shared.port.ClockPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads patient context, runs {@link SafetyEngine}, persists flags, updates item gate/conflict. */
@Service
public class SafetyEvaluationService {

  private static final int ACTIVE_MED_DAYS = 30;

  private final RxDrugDictionary dictionary;
  private final ThresholdSettings thresholds;
  private final RxClinicalQueries clinical;
  private final RxEncounterQueries encounters;
  private final RxPrescriptionQueries prescriptions;
  private final RxItemQueries items;
  private final RxItemMetaRepository itemMetas;
  private final RxSafetyFlagRepository flags;
  private final ClockPort clock;

  public SafetyEvaluationService(
      RxDrugDictionary dictionary,
      ThresholdSettings thresholds,
      RxClinicalQueries clinical,
      RxEncounterQueries encounters,
      RxPrescriptionQueries prescriptions,
      RxItemQueries items,
      RxItemMetaRepository itemMetas,
      RxSafetyFlagRepository flags,
      ClockPort clock) {
    this.dictionary = dictionary;
    this.thresholds = thresholds;
    this.clinical = clinical;
    this.encounters = encounters;
    this.prescriptions = prescriptions;
    this.items = items;
    this.itemMetas = itemMetas;
    this.flags = flags;
    this.clock = clock;
  }

  @Transactional
  public List<SafetyFinding> reevaluate(UUID prescriptionId) {
    Prescription rx =
        prescriptions
            .findById(prescriptionId)
            .orElseThrow(() -> new IllegalArgumentException("prescription not found"));
    List<PrescriptionItem> lineItems = items.findByPrescriptionIdOrderByCreatedAtAsc(prescriptionId);
    PatientSafetyContext ctx = loadContext(rx.getPatientId());
    List<DraftItemView> views = new ArrayList<>();
    for (PrescriptionItem item : lineItems) {
      views.add(toView(item));
    }
    LocalDate today = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC);
    SafetyEngine engine = new SafetyEngine(dictionary, today);
    List<SafetyFinding> findings = engine.evaluate(views, ctx);

    flags.deleteAllForPrescription(prescriptionId);
    for (SafetyFinding f : findings) {
      RxSafetyFlag row = new RxSafetyFlag();
      row.setPrescriptionId(prescriptionId);
      row.setItemId(f.itemId());
      row.setCheckType(f.checkType());
      row.setSeverity(f.severity());
      row.setFingerprint(f.fingerprint());
      row.setMessageKey(f.messageKey());
      row.setDetail(f.detail());
      row.setOverridable(f.overridable());
      flags.save(row);
    }

    BigDecimal threshold = thresholds.confidenceThreshold();
    for (PrescriptionItem item : lineItems) {
      ConflictSeverity max = SafetyEngine.maxSeverityFor(item.getId(), findings);
      item.setConflictSeverity(max);
      RxItemMeta meta = itemMetas.findByItemId(item.getId()).orElse(null);
      if (meta == null) {
        items.save(item);
        continue;
      }
      // Re-apply gate for AI items; MANUAL stays MANUAL unless critical.
      switch (meta.getSource()) {
        case AI -> {
          var status =
              ConfidenceGate.decide(
                  meta.getConfDrug(),
                  meta.getConfDose(),
                  meta.getConfFrequency(),
                  item.getGenericId() != null,
                  max,
                  threshold);
          meta.setGateStatus(status);
          if (status == com.mediflow.prescription.domain.GateStatus.PREFILLED
              && !meta.isResolved()) {
            // prefilled still needs doctor confirmation before sign, but not "amber resolve"
            item.setReviewStatus(ReviewStatus.PENDING);
          } else if (status == com.mediflow.prescription.domain.GateStatus.HARD_STOP) {
            item.setReviewStatus(ReviewStatus.PENDING);
          }
        }
        case MANUAL -> meta.setGateStatus(ConfidenceGate.forManual(max));
      }
      itemMetas.save(meta);
      items.save(item);
    }
    return findings;
  }

  public PatientSafetyContext loadContext(UUID patientId) {
    Patient patient = encounters.patient(patientId).orElse(null);
    BigDecimal weight = null;
    List<VitalRef> vitals = new ArrayList<>();
    for (var v : clinical.vitalsNewestFirst(patientId)) {
      vitals.add(new VitalRef(v.getVitalType(), v.getValueNumeric(), v.getValueText()));
      if (weight == null
          && v.getVitalType() != null
          && v.getVitalType().toUpperCase().contains("WEIGHT")
          && v.getValueNumeric() != null) {
        weight = v.getValueNumeric();
      }
    }
    Instant since = clock.now().minus(ACTIVE_MED_DAYS, ChronoUnit.DAYS);
    List<ActiveMedRef> active = new ArrayList<>();
    for (Prescription signed :
        prescriptions.findByPatientIdAndStatusAndSignedAtAfter(
            patientId, PrescriptionStatus.SIGNED, since)) {
      for (PrescriptionItem it : items.findByPrescriptionIdOrderByCreatedAtAsc(signed.getId())) {
        if (it.getGenericId() == null) {
          continue;
        }
        String gName =
            dictionary.generic(it.getGenericId()).map(GenericInfo::name).orElse(null);
        active.add(new ActiveMedRef(signed.getId(), it.getId(), it.getGenericId(), gName));
      }
    }
    return new PatientSafetyContext(
        patientId,
        patient == null ? null : patient.getDateOfBirth(),
        patient == null ? null : patient.getSex(),
        patient == null ? null : patient.getPregnancyStatus(),
        weight,
        clinical.allergies(patientId).stream()
            .map(a -> new AllergyRef(a.getSubstance(), a.getSubstanceCode()))
            .toList(),
        clinical.conditions(patientId).stream()
            .map(c -> new ConditionRef(c.getIcd10Code(), c.getDisplay()))
            .toList(),
        active,
        vitals);
  }

  public DraftItemView toView(PrescriptionItem item) {
    Optional<BrandInfo> brand =
        item.getBrandId() == null ? Optional.empty() : dictionary.brand(item.getBrandId());
    Optional<GenericInfo> generic =
        item.getGenericId() == null ? Optional.empty() : dictionary.generic(item.getGenericId());
    boolean known = item.getGenericId() != null && generic.isPresent();
    return new DraftItemView(
        item.getId(),
        item.getBrandId(),
        item.getGenericId(),
        brand.map(BrandInfo::name).orElse(item.getRawText()),
        generic.map(GenericInfo::name).orElse(null),
        item.getDose(),
        item.getRoute(),
        item.getFrequency(),
        item.getDuration(),
        known);
  }
}
