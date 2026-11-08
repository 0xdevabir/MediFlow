package com.mediflow.prescription.draft;

import com.mediflow.prescription.domain.RxPrescriptionMeta;
import com.mediflow.prescription.repo.RxEncounterQueries;
import com.mediflow.prescription.repo.RxPrescriptionMetaRepository;
import com.mediflow.prescription.repo.RxPrescriptionQueries;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Doctor-facing draft inbox and encounter deep-link helpers. */
@Component
public class DraftInbox {

  public record EncounterContext(
      UUID encounterId, UUID patientId, String patientName, String mrn) {}

  public record DraftRow(
      UUID prescriptionId,
      UUID encounterId,
      UUID patientId,
      String patientName,
      PrescriptionStatus status,
      String inputMode) {}

  private final RxPrescriptionQueries prescriptions;
  private final RxPrescriptionMetaRepository metas;
  private final RxEncounterQueries encounters;

  public DraftInbox(
      RxPrescriptionQueries prescriptions,
      RxPrescriptionMetaRepository metas,
      RxEncounterQueries encounters) {
    this.prescriptions = prescriptions;
    this.metas = metas;
    this.encounters = encounters;
  }

  @Transactional(readOnly = true)
  public Optional<Prescription> openDraftForEncounter(UUID encounterId) {
    return prescriptions.findByEncounterIdOrderByCreatedAtDesc(encounterId).stream()
        .filter(p -> p.getStatus() == PrescriptionStatus.DRAFT)
        .findFirst();
  }

  @Transactional(readOnly = true)
  public EncounterContext encounterContext(UUID encounterId) {
    Encounter encounter =
        encounters
            .findById(encounterId)
            .orElseThrow(
                () ->
                    new com.mediflow.prescription.support.RxExceptions.ValidationException(
                        "rx.error.encounter", "not found"));
    Patient patient =
        encounters
            .patient(encounter.getPatientId())
            .orElseThrow(
                () ->
                    new com.mediflow.prescription.support.RxExceptions.ValidationException(
                        "rx.error.not_found", "patient"));
    String name =
        ((patient.getGivenName() == null ? "" : patient.getGivenName())
                + " "
                + (patient.getFamilyName() == null ? "" : patient.getFamilyName()))
            .trim();
    return new EncounterContext(encounterId, patient.getId(), name, patient.getMrn());
  }

  @Transactional(readOnly = true)
  public List<DraftRow> draftsNeedingReview(UUID doctorUserId, int limit) {
    List<RxPrescriptionMeta> metasForDoctor =
        metas.findByDoctorUserIdOrderByUpdatedAtDesc(doctorUserId);
    List<DraftRow> rows = new ArrayList<>();
    for (RxPrescriptionMeta meta : metasForDoctor) {
      if (rows.size() >= limit) {
        break;
      }
      Optional<Prescription> rx = prescriptions.findById(meta.getPrescriptionId());
      if (rx.isEmpty() || rx.get().getStatus() != PrescriptionStatus.DRAFT) {
        continue;
      }
      Prescription p = rx.get();
      String patientName =
          encounters
              .patient(p.getPatientId())
              .map(pt -> ((pt.getGivenName() == null ? "" : pt.getGivenName()) + " " + (pt.getFamilyName() == null ? "" : pt.getFamilyName())).trim())
              .orElse("");
      rows.add(
          new DraftRow(
              p.getId(),
              p.getEncounterId(),
              p.getPatientId(),
              patientName,
              p.getStatus(),
              meta.getInputMode() == null ? "" : meta.getInputMode().name()));
    }
    return List.copyOf(rows);
  }
}
