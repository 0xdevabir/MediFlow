package com.mediflow.fhir.service;

import com.mediflow.fhir.FhirIds;
import com.mediflow.fhir.mapper.FhirResourceMapper;
import com.mediflow.fhir.repo.FhirDataStore;
import com.mediflow.fhir.support.FhirAccessDeniedException;
import com.mediflow.fhir.support.FhirBadRequestException;
import com.mediflow.fhir.support.FhirNotFoundException;
import com.mediflow.shared.domain.Allergy;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AuditEvent;
import com.mediflow.shared.domain.ConditionDx;
import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.Department;
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.Dispense;
import com.mediflow.shared.domain.DrugBrand;
import com.mediflow.shared.domain.DrugGeneric;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.RedAlert;
import com.mediflow.shared.domain.Reminder;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.domain.VitalObservation;
import java.util.UUID;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FhirReadService {

  private final FhirDataStore data;
  private final FhirResourceMapper mapper;
  private final FhirAccessGuard access;
  private final FhirAuditService fhirAudit;

  public FhirReadService(
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
  public Resource read(String type, String id) {
    if (type == null || type.isBlank() || id == null || id.isBlank()) {
      throw new FhirBadRequestException("type and id required");
    }
    String resourceType = type.trim();
    String resourceId = id.trim();
    try {
      UUID patientId = resolvePatientId(resourceType, resourceId);
      if (patientId != null) {
        access.requirePatientRead(patientId);
      } else {
        access.requireAuthenticated();
      }
      Resource resource = load(resourceType, resourceId);
      fhirAudit.read(resourceType, FhirIds.parseUuid(resourceId), patientId);
      return resource;
    } catch (FhirAccessDeniedException e) {
      fhirAudit.denied(resourceType, resourceId, null, e.getMessage());
      throw e;
    }
  }

  private Resource load(String type, String id) {
    return switch (type) {
      case "Patient" -> mapper.patient(require(Patient.class, id, type));
      case "Organization" -> mapper.organization(require(Hospital.class, id, type));
      case "Location" -> loadLocation(id);
      case "Practitioner" -> mapper.practitioner(require(Practitioner.class, id, type));
      case "PractitionerRole" -> {
        PractitionerRoleEntity role = require(PractitionerRoleEntity.class, id, type);
        yield mapper.practitionerRole(
            role,
            data.find(Practitioner.class, role.getPractitionerId()).orElse(null),
            data.hospital(role.getHospitalId()).orElse(null),
            data.department(role.getDepartmentId()).orElse(null));
      }
      case "Schedule" -> {
        UUID roleId = parseScheduleId(id);
        PractitionerRoleEntity role =
            require(PractitionerRoleEntity.class, roleId.toString(), type);
        yield mapper.schedule(role);
      }
      case "Slot" -> mapper.slot(require(Slot.class, id, type));
      case "Appointment" -> mapper.appointment(require(Appointment.class, id, type));
      case "Encounter" -> {
        Encounter e = require(Encounter.class, id, type);
        boolean emergency = data.redAlertForEncounter(e.getId()).isPresent();
        yield mapper.encounter(e, emergency);
      }
      case "Flag" -> mapper.redAlertFlag(require(RedAlert.class, id, type));
      case "Observation" -> mapper.observation(require(VitalObservation.class, id, type));
      case "Condition" -> mapper.condition(require(ConditionDx.class, id, type));
      case "AllergyIntolerance" -> mapper.allergy(require(Allergy.class, id, type));
      case "MedicationRequest" -> loadMedicationRequest(id);
      case "MedicationDispense" -> loadMedicationDispense(id);
      case "ServiceRequest" -> {
        LabOrder order = require(LabOrder.class, id, type);
        LabTest test =
            order.getTestId() == null ? null : data.labTest(order.getTestId()).orElse(null);
        yield mapper.serviceRequest(order, test);
      }
      case "DiagnosticReport" -> {
        DiagnosticReport report = require(DiagnosticReport.class, id, type);
        LabOrder order = data.labOrder(report.getLabOrderId()).orElse(null);
        LabTest test =
            order != null && order.getTestId() != null
                ? data.labTest(order.getTestId()).orElse(null)
                : null;
        yield mapper.diagnosticReport(report, order, test);
      }
      case "Consent" -> mapper.consent(require(Consent.class, id, type));
      case "AuditEvent" -> mapper.auditEvent(require(AuditEvent.class, id, type));
      case "Provenance" -> {
        UUID auditId = parseProvId(id);
        yield mapper.provenance(require(AuditEvent.class, auditId.toString(), type));
      }
      case "Communication" -> mapper.communication(require(Notification.class, id, type));
      case "Task" -> mapper.task(require(Reminder.class, id, type));
      default -> throw new FhirBadRequestException("unsupported resource type: " + type);
    };
  }

  private Resource loadLocation(String id) {
    if (id.startsWith("hospital-")) {
      UUID hid = FhirIds.parseUuid(id.substring("hospital-".length()));
      Hospital h =
          data.hospital(hid).orElseThrow(() -> new FhirNotFoundException("Location", id));
      return mapper.location(h);
    }
    if (id.startsWith("dept-")) {
      UUID did = FhirIds.parseUuid(id.substring("dept-".length()));
      Department d =
          data.department(did).orElseThrow(() -> new FhirNotFoundException("Location", id));
      Hospital h = data.hospital(d.getHospitalId()).orElse(null);
      return mapper.location(d, h);
    }
    UUID hid = FhirIds.parseUuid(id);
    Hospital h =
        data.hospital(hid).orElseThrow(() -> new FhirNotFoundException("Location", id));
    return mapper.location(h);
  }

  private Resource loadMedicationRequest(String id) {
    PrescriptionItem item = require(PrescriptionItem.class, id, "MedicationRequest");
    Prescription rx =
        data.prescription(item.getPrescriptionId())
            .orElseThrow(() -> new FhirNotFoundException("MedicationRequest", id));
    DrugGeneric generic = data.generic(item.getGenericId()).orElse(null);
    DrugBrand brand =
        item.getBrandId() == null ? null : data.brand(item.getBrandId()).orElse(null);
    return mapper.medicationRequest(rx, item, generic, brand);
  }

  private Resource loadMedicationDispense(String id) {
    Dispense d = require(Dispense.class, id, "MedicationDispense");
    Prescription rx = data.prescription(d.getPrescriptionId()).orElse(null);
    PrescriptionItem item =
        d.getPrescriptionItemId() == null
            ? null
            : data.find(PrescriptionItem.class, d.getPrescriptionItemId()).orElse(null);
    DrugGeneric generic = item == null ? null : data.generic(item.getGenericId()).orElse(null);
    return mapper.medicationDispense(d, rx, item, generic);
  }

  private UUID resolvePatientId(String type, String id) {
    return switch (type) {
      case "Patient" -> FhirIds.parseUuid(id);
      case "Appointment" ->
          data.find(Appointment.class, FhirIds.parseUuid(id))
              .map(Appointment::getPatientId)
              .orElse(null);
      case "Encounter" ->
          data.find(Encounter.class, FhirIds.parseUuid(id))
              .map(Encounter::getPatientId)
              .orElse(null);
      case "Flag" ->
          data.find(RedAlert.class, FhirIds.parseUuid(id)).map(RedAlert::getPatientId).orElse(null);
      case "Observation" ->
          data.find(VitalObservation.class, FhirIds.parseUuid(id))
              .map(VitalObservation::getPatientId)
              .orElse(null);
      case "Condition" ->
          data.find(ConditionDx.class, FhirIds.parseUuid(id))
              .map(ConditionDx::getPatientId)
              .orElse(null);
      case "AllergyIntolerance" ->
          data.find(Allergy.class, FhirIds.parseUuid(id)).map(Allergy::getPatientId).orElse(null);
      case "MedicationRequest" ->
          data.find(PrescriptionItem.class, FhirIds.parseUuid(id))
              .flatMap(i -> data.prescription(i.getPrescriptionId()))
              .map(Prescription::getPatientId)
              .orElse(null);
      case "MedicationDispense" ->
          data.find(Dispense.class, FhirIds.parseUuid(id))
              .flatMap(d -> data.prescription(d.getPrescriptionId()))
              .map(Prescription::getPatientId)
              .orElse(null);
      case "ServiceRequest" ->
          data.find(LabOrder.class, FhirIds.parseUuid(id)).map(LabOrder::getPatientId).orElse(null);
      case "DiagnosticReport" ->
          data.find(DiagnosticReport.class, FhirIds.parseUuid(id))
              .flatMap(r -> data.labOrder(r.getLabOrderId()))
              .map(LabOrder::getPatientId)
              .orElse(null);
      case "Consent" ->
          data.find(Consent.class, FhirIds.parseUuid(id)).map(Consent::getPatientId).orElse(null);
      case "AuditEvent" ->
          data.find(AuditEvent.class, FhirIds.parseUuid(id))
              .map(AuditEvent::getPatientId)
              .orElse(null);
      case "Provenance" ->
          data.find(AuditEvent.class, parseProvId(id)).map(AuditEvent::getPatientId).orElse(null);
      case "Communication" ->
          data.find(Notification.class, FhirIds.parseUuid(id))
              .map(Notification::getPatientId)
              .orElse(null);
      case "Task" ->
          data.find(Reminder.class, FhirIds.parseUuid(id)).map(Reminder::getPatientId).orElse(null);
      default -> null;
    };
  }

  private <T> T require(Class<T> type, String id, String fhirType) {
    UUID uuid = FhirIds.parseUuid(id);
    if (uuid == null) {
      throw new FhirBadRequestException("invalid id: " + id);
    }
    return data.find(type, uuid).orElseThrow(() -> new FhirNotFoundException(fhirType, id));
  }

  private static UUID parseScheduleId(String id) {
    String raw = id.startsWith("schedule-") ? id.substring("schedule-".length()) : id;
    UUID uuid = FhirIds.parseUuid(raw);
    if (uuid == null) {
      throw new FhirBadRequestException("invalid Schedule id: " + id);
    }
    return uuid;
  }

  private static UUID parseProvId(String id) {
    String raw = id.startsWith("prov-") ? id.substring("prov-".length()) : id;
    UUID uuid = FhirIds.parseUuid(raw);
    if (uuid == null) {
      throw new FhirBadRequestException("invalid Provenance id: " + id);
    }
    return uuid;
  }

  public static boolean isSupported(String type) {
    if (type == null) {
      return false;
    }
    return switch (type.trim()) {
      case "Patient",
          "Organization",
          "Location",
          "Practitioner",
          "PractitionerRole",
          "Schedule",
          "Slot",
          "Appointment",
          "Encounter",
          "Flag",
          "Observation",
          "Condition",
          "AllergyIntolerance",
          "MedicationRequest",
          "MedicationDispense",
          "ServiceRequest",
          "DiagnosticReport",
          "Consent",
          "AuditEvent",
          "Provenance",
          "Communication",
          "Task" -> true;
      default -> false;
    };
  }
}
