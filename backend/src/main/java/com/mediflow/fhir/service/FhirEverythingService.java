package com.mediflow.fhir.service;

import com.mediflow.fhir.FhirIds;
import com.mediflow.fhir.mapper.FhirResourceMapper;
import com.mediflow.fhir.repo.FhirDataStore;
import com.mediflow.shared.domain.Allergy;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AuditEvent;
import com.mediflow.shared.domain.ConditionDx;
import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.Dispense;
import com.mediflow.shared.domain.DrugBrand;
import com.mediflow.shared.domain.DrugGeneric;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.RedAlert;
import com.mediflow.shared.domain.Reminder;
import com.mediflow.shared.domain.VitalObservation;
import java.util.Date;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** IPS-style Patient/$everything summary bundle. */
@Service
public class FhirEverythingService {

  private final FhirDataStore data;
  private final FhirResourceMapper mapper;
  private final FhirAccessGuard access;
  private final FhirAuditService fhirAudit;

  public FhirEverythingService(
      FhirDataStore data,
      FhirResourceMapper mapper,
      FhirAccessGuard access,
      FhirAuditService fhirAudit) {
    this.data = data;
    this.mapper = mapper;
    this.access = access;
    this.fhirAudit = fhirAudit;
  }

  @Transactional(readOnly = true)
  public Bundle everything(UUID patientId) {
    access.requirePatientRead(patientId);
    Patient patient =
        data.find(Patient.class, patientId)
            .orElseThrow(
                () ->
                    new com.mediflow.fhir.support.FhirNotFoundException(
                        "Patient", String.valueOf(patientId)));

    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.SEARCHSET);
    bundle.setTimestamp(new Date());
    add(bundle, mapper.patient(patient));

    for (Allergy a : data.allergiesForPatient(patientId)) {
      add(bundle, mapper.allergy(a));
    }
    for (ConditionDx dx : data.conditionsForPatient(patientId)) {
      add(bundle, mapper.condition(dx));
    }
    for (VitalObservation v : data.vitalsForPatient(patientId)) {
      add(bundle, mapper.observation(v));
    }
    for (Encounter e : data.encountersForPatient(patientId)) {
      boolean emergency = data.redAlertForEncounter(e.getId()).isPresent();
      add(bundle, mapper.encounter(e, emergency));
    }
    for (RedAlert alert : data.redAlertsForPatient(patientId)) {
      add(bundle, mapper.redAlertFlag(alert));
    }
    for (Appointment a : data.appointmentsForPatient(patientId)) {
      add(bundle, mapper.appointment(a));
    }
    for (Prescription rx : data.prescriptionsForPatient(patientId)) {
      for (PrescriptionItem item : data.itemsForPrescription(rx.getId())) {
        DrugGeneric generic = data.generic(item.getGenericId()).orElse(null);
        DrugBrand brand =
            item.getBrandId() == null ? null : data.brand(item.getBrandId()).orElse(null);
        add(bundle, mapper.medicationRequest(rx, item, generic, brand));
      }
    }
    for (Dispense d : data.dispensesForPatient(patientId)) {
      Prescription rx = data.prescription(d.getPrescriptionId()).orElse(null);
      PrescriptionItem item =
          d.getPrescriptionItemId() == null
              ? null
              : data.find(PrescriptionItem.class, d.getPrescriptionItemId()).orElse(null);
      DrugGeneric generic =
          item == null ? null : data.generic(item.getGenericId()).orElse(null);
      add(bundle, mapper.medicationDispense(d, rx, item, generic));
    }
    for (LabOrder order : data.labOrdersForPatient(patientId)) {
      LabTest test =
          order.getTestId() == null ? null : data.labTest(order.getTestId()).orElse(null);
      add(bundle, mapper.serviceRequest(order, test));
    }
    for (DiagnosticReport report : data.reportsForPatient(patientId)) {
      LabOrder order = data.labOrder(report.getLabOrderId()).orElse(null);
      LabTest test =
          order != null && order.getTestId() != null
              ? data.labTest(order.getTestId()).orElse(null)
              : null;
      add(bundle, mapper.diagnosticReport(report, order, test));
    }
    for (Consent c : data.consentsForPatient(patientId)) {
      add(bundle, mapper.consent(c));
    }
    for (Notification n : data.notificationsForPatient(patientId)) {
      add(bundle, mapper.communication(n));
    }
    for (Reminder r : data.remindersForPatient(patientId)) {
      add(bundle, mapper.task(r));
    }
    for (AuditEvent e : data.auditForPatient(patientId)) {
      add(bundle, mapper.auditEvent(e));
      add(bundle, mapper.provenance(e));
    }

    bundle.setTotal(bundle.getEntry().size());
    fhirAudit.read("Patient", patientId, patientId);
    return bundle;
  }

  private static void add(Bundle bundle, Resource resource) {
    if (resource == null) {
      return;
    }
    Bundle.BundleEntryComponent entry = bundle.addEntry();
    entry.setResource(resource);
    if (resource.getIdElement() != null && resource.getIdElement().getIdPart() != null) {
      entry.setFullUrl(resource.getResourceType().name() + "/" + resource.getIdElement().getIdPart());
    }
  }
}
