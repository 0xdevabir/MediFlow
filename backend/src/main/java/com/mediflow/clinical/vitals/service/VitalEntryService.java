package com.mediflow.clinical.vitals.service;

import com.mediflow.clinical.consultation.port.ClinicalAccess;
import com.mediflow.clinical.vitals.domain.Population;
import com.mediflow.clinical.vitals.domain.VitalReading;
import com.mediflow.clinical.vitals.domain.VitalSetClassifier;
import com.mediflow.clinical.vitals.domain.VitalSetResult;
import com.mediflow.clinical.vitals.domain.VitalSign;
import com.mediflow.clinical.vitals.domain.VitalType;
import com.mediflow.clinical.vitals.repo.ClinicalVitalObservationRepository;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.VitalClass;
import com.mediflow.shared.domain.VitalObservation;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.EncounterRepository;
import com.mediflow.shared.repo.PatientRepository;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VitalEntryService {

  public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Dhaka");

  private final ClinicalVitalObservationRepository observations;
  private final EncounterRepository encounters;
  private final PatientRepository patients;
  private final VitalCatalogService catalog;
  private final VitalRuleCache ruleCache;
  private final ClinicalAccess access;
  private final AuditPort audit;
  private final ClockPort clock;
  private final CurrentUser currentUser;
  private final ApplicationEventPublisher events;

  public VitalEntryService(
      ClinicalVitalObservationRepository observations,
      EncounterRepository encounters,
      PatientRepository patients,
      VitalCatalogService catalog,
      VitalRuleCache ruleCache,
      ClinicalAccess access,
      AuditPort audit,
      ClockPort clock,
      CurrentUser currentUser,
      ApplicationEventPublisher events) {
    this.observations = observations;
    this.encounters = encounters;
    this.patients = patients;
    this.catalog = catalog;
    this.ruleCache = ruleCache;
    this.access = access;
    this.audit = audit;
    this.clock = clock;
    this.currentUser = currentUser;
    this.events = events;
  }

  /** Classify without persisting — for live colour feedback on the form. */
  @PreAuthorize("hasAnyRole('NURSE','DOCTOR','ER','ADMIN')")
  @Transactional(readOnly = true)
  public VitalSetResult preview(UUID encounterId, Map<String, String> rawByCode) {
    Encounter encounter = requireEncounter(encounterId);
    requireVitalsWrite(encounter.getPatientId());
    Patient patient = requirePatient(encounter.getPatientId());
    return classify(patient, rawByCode);
  }

  @PreAuthorize("hasAnyRole('NURSE','DOCTOR','ER','ADMIN')")
  @Transactional
  public VitalSetResult record(UUID encounterId, Map<String, String> rawByCode) {
    Encounter encounter = requireEncounter(encounterId);
    requireVitalsWrite(encounter.getPatientId());
    Patient patient = requirePatient(encounter.getPatientId());
    VitalSetResult result = classify(patient, rawByCode);
    Instant now = clock.now();
    UUID actor = currentUser.requireUserId();

    List<VitalObservation> saved = new ArrayList<>();
    for (VitalReading reading : result.readings()) {
      if (reading.derived()) {
        continue; // derived values are computed on read; store entered vitals only
      }
      VitalObservation obs = new VitalObservation();
      obs.setEncounterId(encounter.getId());
      obs.setPatientId(patient.getId());
      obs.setVitalType(reading.code());
      obs.setValueNumeric(reading.sign().numericValue());
      obs.setValueText(reading.sign().textValue());
      obs.setUnit(reading.sign().type().ucumUnit());
      obs.setClassification(reading.level());
      obs.setRecordedBy(actor);
      obs.setRecordedAt(now);
      saved.add(observations.save(obs));
    }

    events.publishEvent(
        new DomainEvents.VitalsClassified(
            encounter.getId(), patient.getId(), result.patientLevel(), now));

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("patientLevel", result.patientLevel().name());
    details.put("count", saved.size());
    if (result.pulsePressure() != null) {
      details.put("pulsePressure", result.pulsePressure());
    }
    audit.record(actor, patient.getId(), "VITALS_RECORDED", "Encounter", encounter.getId(), details);
    return result;
  }

  @Transactional(readOnly = true)
  public List<VitalObservation> trend(UUID patientId, String vitalCode, int days) {
    requireVitalsRead(patientId);
    Instant since = clock.now().minusSeconds(Math.max(1, days) * 86400L);
    audit.record(
        currentUser.userId().orElse(null),
        patientId,
        "VITALS_TREND_READ",
        "Patient",
        patientId,
        Map.of("vitalType", vitalCode, "days", days));
    return observations
        .findByPatientIdAndVitalTypeAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
            patientId, vitalCode, since);
  }

  private VitalSetResult classify(Patient patient, Map<String, String> rawByCode) {
    LocalDate today = LocalDate.ofInstant(clock.now(), CLINIC_ZONE);
    Population population =
        Population.of(patient.getDateOfBirth(), patient.getPregnancyStatus(), today);
    List<VitalSign> signs = new ArrayList<>();
    for (Map.Entry<String, String> e : rawByCode.entrySet()) {
      if (e.getValue() == null || e.getValue().isBlank()) {
        continue;
      }
      VitalType type = catalog.require(e.getKey());
      if (type.kind().name().equals("DERIVED")) {
        continue;
      }
      signs.add(VitalSign.parse(type, e.getValue()));
    }
    VitalSetClassifier classifier =
        new VitalSetClassifier(catalog.mapType(), catalog.shockIndexType());
    return classifier.classify(signs, ruleCache.forPopulation(population));
  }

  private void requireVitalsWrite(UUID patientId) {
    if (!access.canWriteVitals(patientId)) {
      throw new AccessDeniedException("Not allowed to record vitals for patient " + patientId);
    }
  }

  private void requireVitalsRead(UUID patientId) {
    if (!access.canReadPatient(patientId)) {
      throw new AccessDeniedException("Not allowed to read vitals for patient " + patientId);
    }
  }

  private Encounter requireEncounter(UUID id) {
    return encounters
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Encounter not found: " + id));
  }

  private Patient requirePatient(UUID id) {
    return patients
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + id));
  }

  public record ClassColour(VitalClass level, String cssClass) {
    public static ClassColour of(VitalClass level) {
      return switch (level) {
        case NORMAL -> new ClassColour(level, "vital-normal");
        case BORDERLINE -> new ClassColour(level, "vital-borderline");
        case CRITICAL -> new ClassColour(level, "vital-critical");
      };
    }
  }
}
