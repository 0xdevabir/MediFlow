package com.mediflow.fhir.mapper;

import com.mediflow.fhir.FhirIds;
import com.mediflow.fhir.FhirSystems;
import com.mediflow.fhir.clinical.VitalLoinc;
import com.mediflow.shared.domain.Allergy;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.AuditEvent;
import com.mediflow.shared.domain.ConditionDx;
import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.Department;
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.Dispense;
import com.mediflow.shared.domain.DrugBrand;
import com.mediflow.shared.domain.DrugGeneric;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.EncounterStatus;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.OrderType;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.RedAlert;
import com.mediflow.shared.domain.Reminder;
import com.mediflow.shared.domain.ReminderStatus;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.domain.VitalObservation;
import java.util.List;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAgentComponent;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventEntityComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Communication;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Flag;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Schedule;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.springframework.stereotype.Component;

@Component
public class FhirResourceMapper {

  private final FhirSystems systems;

  public FhirResourceMapper(FhirSystems systems) {
    this.systems = systems;
  }

  public org.hl7.fhir.r4.model.Patient patient(Patient p) {
    org.hl7.fhir.r4.model.Patient out = new org.hl7.fhir.r4.model.Patient();
    out.setId(FhirIds.id(p.getId()));
    if (p.getMrn() != null) {
      out.addIdentifier(new Identifier().setSystem(systems.mrn()).setValue(p.getMrn()));
    }
    if (p.getNationalId() != null) {
      out.addIdentifier(
          new Identifier().setSystem(systems.nationalId()).setValue(p.getNationalId()));
    }
    HumanName name = out.addName();
    name.setFamily(p.getFamilyName());
    if (p.getGivenName() != null) {
      name.addGiven(p.getGivenName());
    }
    out.setBirthDate(MapperSupport.date(p.getDateOfBirth()));
    out.setGender(mapGender(p.getSex()));
    if (p.getPhone() != null) {
      out.addTelecom(
          new ContactPoint()
              .setSystem(ContactPoint.ContactPointSystem.PHONE)
              .setValue(p.getPhone()));
    }
    if (p.getEmail() != null) {
      out.addTelecom(
          new ContactPoint()
              .setSystem(ContactPoint.ContactPointSystem.EMAIL)
              .setValue(p.getEmail()));
    }
    if (p.getAddress() != null) {
      out.addAddress().setText(p.getAddress());
    }
    return out;
  }

  public Organization organization(Hospital h) {
    Organization org = new Organization();
    org.setId(FhirIds.id(h.getId()));
    org.setName(h.getName());
    org.setActive(h.getActive());
    if (h.getType() != null) {
      org.addType(MapperSupport.text(h.getType()));
    }
    if (h.getPhone() != null) {
      org.addTelecom(
          new ContactPoint()
              .setSystem(ContactPoint.ContactPointSystem.PHONE)
              .setValue(h.getPhone()));
    }
    if (h.getAddress() != null) {
      org.addAddress().setText(h.getAddress());
    }
    return org;
  }

  public Location location(Hospital h) {
    Location loc = new Location();
    loc.setId("hospital-" + FhirIds.id(h.getId()));
    loc.setName(h.getName());
    loc.setStatus(Location.LocationStatus.ACTIVE);
    loc.setManagingOrganization(FhirIds.ref(ResourceType.Organization, h.getId(), h.getName()));
    if (h.getAddress() != null) {
      loc.getAddress().setText(h.getAddress());
    }
    if (h.getPhone() != null) {
      loc.addTelecom(
          new ContactPoint()
              .setSystem(ContactPoint.ContactPointSystem.PHONE)
              .setValue(h.getPhone()));
    }
    return loc;
  }

  public Location location(Department d, Hospital hospital) {
    Location loc = new Location();
    loc.setId("dept-" + FhirIds.id(d.getId()));
    loc.setName(d.getName());
    loc.setStatus(Location.LocationStatus.ACTIVE);
    if (hospital != null) {
      loc.setManagingOrganization(
          FhirIds.ref(ResourceType.Organization, hospital.getId(), hospital.getName()));
      loc.setPartOf(new Reference("Location/hospital-" + hospital.getId()));
    }
    return loc;
  }

  public org.hl7.fhir.r4.model.Practitioner practitioner(Practitioner p) {
    org.hl7.fhir.r4.model.Practitioner out = new org.hl7.fhir.r4.model.Practitioner();
    out.setId(FhirIds.id(p.getId()));
    out.setActive(true);
    if (p.getLicenceNo() != null) {
      out.addIdentifier(new Identifier().setSystem(systems.licence()).setValue(p.getLicenceNo()));
    }
    HumanName name = out.addName();
    name.setText(p.getName());
    if (p.getRoleLabel() != null) {
      out.addQualification().setCode(MapperSupport.text(p.getRoleLabel()));
    }
    return out;
  }

  public org.hl7.fhir.r4.model.PractitionerRole practitionerRole(
      PractitionerRoleEntity role, Practitioner practitioner, Hospital hospital, Department dept) {
    org.hl7.fhir.r4.model.PractitionerRole out = new org.hl7.fhir.r4.model.PractitionerRole();
    out.setId(FhirIds.id(role.getId()));
    out.setActive(role.getActive());
    out.setPractitioner(
        FhirIds.ref(
            ResourceType.Practitioner,
            role.getPractitionerId(),
            practitioner == null ? null : practitioner.getName()));
    if (hospital != null) {
      out.setOrganization(
          FhirIds.ref(ResourceType.Organization, hospital.getId(), hospital.getName()));
    }
    if (dept != null) {
      out.addLocation(new Reference("Location/dept-" + dept.getId()).setDisplay(dept.getName()));
    }
    if (practitioner != null && practitioner.getRoleLabel() != null) {
      out.addCode(MapperSupport.text(practitioner.getRoleLabel()));
    }
    return out;
  }

  public Schedule schedule(PractitionerRoleEntity role) {
    Schedule s = new Schedule();
    s.setId("schedule-" + FhirIds.id(role.getId()));
    s.setActive(role.getActive());
    s.addActor(FhirIds.ref(ResourceType.PractitionerRole, role.getId()));
    if (role.getSchedule() != null) {
      s.setComment(role.getSchedule());
    }
    return s;
  }

  public org.hl7.fhir.r4.model.Slot slot(Slot slot) {
    org.hl7.fhir.r4.model.Slot out = new org.hl7.fhir.r4.model.Slot();
    out.setId(FhirIds.id(slot.getId()));
    out.setSchedule(new Reference("Schedule/schedule-" + slot.getPractitionerRoleId()));
    out.setStart(MapperSupport.date(slot.getStartsAt()));
    out.setEnd(MapperSupport.date(slot.getEndsAt()));
    boolean free = slot.getBookedCount() < slot.getCapacity();
    out.setStatus(
        free
            ? org.hl7.fhir.r4.model.Slot.SlotStatus.FREE
            : org.hl7.fhir.r4.model.Slot.SlotStatus.BUSY);
    out.setComment("capacity=" + slot.getCapacity() + ";booked=" + slot.getBookedCount());
    return out;
  }

  public org.hl7.fhir.r4.model.Appointment appointment(Appointment a) {
    org.hl7.fhir.r4.model.Appointment out = new org.hl7.fhir.r4.model.Appointment();
    out.setId(FhirIds.id(a.getId()));
    out.setStatus(mapAppointmentStatus(a.getStatus()));
    out.setCreated(MapperSupport.date(a.getBookedAt()));
    out.addParticipant()
        .setActor(FhirIds.ref(ResourceType.Patient, a.getPatientId()))
        .setStatus(org.hl7.fhir.r4.model.Appointment.ParticipationStatus.ACCEPTED);
    if (a.getDoctorId() != null) {
      out.addParticipant()
          .setActor(FhirIds.ref(ResourceType.Practitioner, a.getDoctorId()))
          .setStatus(org.hl7.fhir.r4.model.Appointment.ParticipationStatus.ACCEPTED);
    }
    if (a.getSlotId() != null) {
      out.addSlot(FhirIds.ref(ResourceType.Slot, a.getSlotId()));
    }
    if (a.getHospitalId() != null) {
      out.addParticipant()
          .setActor(FhirIds.ref(ResourceType.Location, a.getHospitalId()))
          .setStatus(org.hl7.fhir.r4.model.Appointment.ParticipationStatus.ACCEPTED);
    }
    if (a.getPriorityLevel() != null) {
      out.setPriority(a.getPriorityLevel().ordinal() + 1);
    }
    return out;
  }

  public org.hl7.fhir.r4.model.Encounter encounter(Encounter e, boolean emergency) {
    org.hl7.fhir.r4.model.Encounter out = new org.hl7.fhir.r4.model.Encounter();
    out.setId(FhirIds.id(e.getId()));
    out.setStatus(mapEncounterStatus(e.getStatus()));
    out.setClass_(
        new Coding()
            .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
            .setCode(emergency ? "EMER" : "AMB")
            .setDisplay(emergency ? "emergency" : "ambulatory"));
    out.setSubject(FhirIds.ref(ResourceType.Patient, e.getPatientId()));
    if (e.getDoctorId() != null) {
      out.addParticipant().setIndividual(FhirIds.ref(ResourceType.Practitioner, e.getDoctorId()));
    }
    if (e.getHospitalId() != null) {
      out.addLocation()
          .setLocation(new Reference("Location/hospital-" + e.getHospitalId()));
      out.setServiceProvider(FhirIds.ref(ResourceType.Organization, e.getHospitalId()));
    }
    if (e.getAppointmentId() != null) {
      out.addAppointment(FhirIds.ref(ResourceType.Appointment, e.getAppointmentId()));
    }
    Period period = new Period().setStart(MapperSupport.date(e.getStartedAt()));
    if (e.getEndedAt() != null) {
      period.setEnd(MapperSupport.date(e.getEndedAt()));
    }
    out.setPeriod(period);
    if (e.getDiagnosisSummary() != null) {
      out.addReasonCode(MapperSupport.text(e.getDiagnosisSummary()));
    }
    return out;
  }

  public Flag redAlertFlag(RedAlert alert) {
    Flag flag = new Flag();
    flag.setId(FhirIds.id(alert.getId()));
    flag.setStatus(
        alert.getAcknowledgedAt() == null ? Flag.FlagStatus.ACTIVE : Flag.FlagStatus.INACTIVE);
    flag.setSubject(FhirIds.ref(ResourceType.Patient, alert.getPatientId()));
    if (alert.getEncounterId() != null) {
      flag.setEncounter(FhirIds.ref(ResourceType.Encounter, alert.getEncounterId()));
    }
    flag.setCode(
        MapperSupport.coding(
            systems.redAlert(),
            alert.getLevel() == null ? "ALERT" : alert.getLevel().name(),
            alert.getReason() == null ? "Red Alert" : alert.getReason()));
    Period period = new Period().setStart(MapperSupport.date(alert.getRaisedAt()));
    if (alert.getAcknowledgedAt() != null) {
      period.setEnd(MapperSupport.date(alert.getAcknowledgedAt()));
    }
    flag.setPeriod(period);
    return flag;
  }

  public Observation observation(VitalObservation v) {
    Observation obs = new Observation();
    obs.setId(FhirIds.id(v.getId()));
    obs.setStatus(Observation.ObservationStatus.FINAL);
    Meta meta = new Meta();
    meta.addProfile(FhirSystems.VITAL_SIGNS_PROFILE);
    obs.setMeta(meta);
    obs.addCategory(
        MapperSupport.coding(FhirSystems.VITAL_SIGNS_CATEGORY, "vital-signs", "Vital Signs"));
    String[] loinc = VitalLoinc.forType(v.getVitalType());
    obs.setCode(MapperSupport.coding(FhirSystems.LOINC, loinc[0], loinc[1]));
    obs.setSubject(FhirIds.ref(ResourceType.Patient, v.getPatientId()));
    if (v.getEncounterId() != null) {
      obs.setEncounter(FhirIds.ref(ResourceType.Encounter, v.getEncounterId()));
    }
    obs.setEffective(new org.hl7.fhir.r4.model.DateTimeType(MapperSupport.date(v.getRecordedAt())));
    String unit = v.getUnit() != null ? v.getUnit() : loinc[2];
    if (v.getValueNumeric() != null) {
      obs.setValue(MapperSupport.quantity(v.getValueNumeric(), unit));
    } else if (v.getValueText() != null) {
      if ("BP".equalsIgnoreCase(v.getVitalType()) && v.getValueText().contains("/")) {
        String[] parts = v.getValueText().split("/", 2);
        try {
          Observation.ObservationComponentComponent sys = obs.addComponent();
          sys.setCode(
              MapperSupport.coding(
                  FhirSystems.LOINC, "8480-6", "Systolic blood pressure"));
          sys.setValue(
              MapperSupport.quantity(new java.math.BigDecimal(parts[0].trim()), unit));
          Observation.ObservationComponentComponent dia = obs.addComponent();
          dia.setCode(
              MapperSupport.coding(
                  FhirSystems.LOINC, "8462-4", "Diastolic blood pressure"));
          dia.setValue(
              MapperSupport.quantity(new java.math.BigDecimal(parts[1].trim()), unit));
        } catch (NumberFormatException ex) {
          obs.setValue(new StringType(v.getValueText()));
        }
      } else {
        obs.setValue(new StringType(v.getValueText()));
      }
    }
    if (v.getClassification() != null) {
      obs.addInterpretation(MapperSupport.text(v.getClassification().name()));
    }
    return obs;
  }

  public Condition condition(ConditionDx dx) {
    Condition c = new Condition();
    c.setId(FhirIds.id(dx.getId()));
    c.setSubject(FhirIds.ref(ResourceType.Patient, dx.getPatientId()));
    if (dx.getEncounterId() != null) {
      c.setEncounter(FhirIds.ref(ResourceType.Encounter, dx.getEncounterId()));
    }
    c.setCode(MapperSupport.coding(FhirSystems.ICD10, dx.getIcd10Code(), dx.getDisplay()));
    c.setClinicalStatus(
        MapperSupport.coding(
            "http://terminology.hl7.org/CodeSystem/condition-clinical",
            dx.getClinicalStatus() == null ? "active" : dx.getClinicalStatus().toLowerCase(),
            dx.getClinicalStatus()));
    c.setRecordedDate(MapperSupport.date(dx.getRecordedAt()));
    if (dx.getRecordedBy() != null) {
      c.setRecorder(FhirIds.ref(ResourceType.Practitioner, dx.getRecordedBy()));
    }
    return c;
  }

  public AllergyIntolerance allergy(Allergy a) {
    AllergyIntolerance ai = new AllergyIntolerance();
    ai.setId(FhirIds.id(a.getId()));
    ai.setPatient(FhirIds.ref(ResourceType.Patient, a.getPatientId()));
    ai.setClinicalStatus(
        MapperSupport.coding(
            "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
            "active",
            "Active"));
    if (a.getSubstanceCode() != null && a.getSubstanceCode().startsWith("ATC")) {
      ai.setCode(MapperSupport.coding(FhirSystems.ATC, a.getSubstanceCode(), a.getSubstance()));
    } else if (a.getSubstanceCode() != null) {
      ai.setCode(MapperSupport.coding(FhirSystems.RXNORM, a.getSubstanceCode(), a.getSubstance()));
    } else {
      ai.setCode(MapperSupport.text(a.getSubstance()));
    }
    if (a.getCriticality() != null) {
      try {
        ai.setCriticality(
            AllergyIntolerance.AllergyIntoleranceCriticality.fromCode(
                a.getCriticality().toLowerCase()));
      } catch (Exception ignored) {
        ai.setCriticality(AllergyIntolerance.AllergyIntoleranceCriticality.UNABLETOASSESS);
      }
    }
    if (a.getNote() != null) {
      ai.addNote(new Annotation().setText(a.getNote()));
    }
    return ai;
  }

  public MedicationRequest medicationRequest(
      Prescription rx, PrescriptionItem item, DrugGeneric generic, DrugBrand brand) {
    MedicationRequest mr = new MedicationRequest();
    mr.setId(FhirIds.id(item.getId()));
    mr.setSubject(FhirIds.ref(ResourceType.Patient, rx.getPatientId()));
    if (rx.getEncounterId() != null) {
      mr.setEncounter(FhirIds.ref(ResourceType.Encounter, rx.getEncounterId()));
    }
    mr.setStatus(mapMedReqStatus(rx.getStatus()));
    mr.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
    if (rx.getSignedBy() != null) {
      mr.setRequester(FhirIds.ref(ResourceType.Practitioner, rx.getSignedBy()));
    }
    if (rx.getSignedAt() != null) {
      mr.setAuthoredOn(MapperSupport.date(rx.getSignedAt()));
    }
    CodeableConcept med = new CodeableConcept();
    if (generic != null) {
      if (generic.getRxnormCode() != null) {
        med.addCoding(
            new Coding(FhirSystems.RXNORM, generic.getRxnormCode(), generic.getName()));
      }
      if (generic.getAtcCode() != null) {
        med.addCoding(new Coding(FhirSystems.ATC, generic.getAtcCode(), generic.getName()));
      }
      med.setText(generic.getName());
    }
    if (brand != null) {
      if (med.getText() == null) {
        med.setText(brand.getName());
      }
      Extension dgda = MapperSupport.dgdaBrand(systems.dgdaBrand(), brand.getDgdaRegNo());
      if (dgda != null) {
        mr.addExtension(dgda);
      }
      mr.addExtension(
          new Extension(
              systems.dgdaBrandName(),
              new StringType(brand.getName())));
    }
    mr.setMedication(med);
    org.hl7.fhir.r4.model.Dosage dose = mr.addDosageInstruction();
    if (item.getDose() != null) {
      dose.setText(item.getDose());
    }
    if (item.getRoute() != null) {
      dose.setRoute(MapperSupport.text(item.getRoute()));
    }
    if (item.getFrequency() != null) {
      org.hl7.fhir.r4.model.Timing timing = new org.hl7.fhir.r4.model.Timing();
      timing.setCode(MapperSupport.text(item.getFrequency()));
      dose.setTiming(timing);
    }
    if (item.getDuration() != null) {
      dose.addAdditionalInstruction(MapperSupport.text("duration: " + item.getDuration()));
    }
    return mr;
  }

  public MedicationDispense medicationDispense(
      Dispense d, Prescription rx, PrescriptionItem item, DrugGeneric generic) {
    MedicationDispense md = new MedicationDispense();
    md.setId(FhirIds.id(d.getId()));
    if (rx != null) {
      md.setSubject(FhirIds.ref(ResourceType.Patient, rx.getPatientId()));
    }
    md.setStatus(MedicationDispense.MedicationDispenseStatus.COMPLETED);
    if (item != null) {
      md.addAuthorizingPrescription(FhirIds.ref(ResourceType.MedicationRequest, item.getId()));
    }
    if (generic != null) {
      CodeableConcept med = new CodeableConcept();
      if (generic.getAtcCode() != null) {
        med.addCoding(new Coding(FhirSystems.ATC, generic.getAtcCode(), generic.getName()));
      }
      med.setText(generic.getName());
      md.setMedication(med);
    }
    if (d.getQuantity() != null) {
      md.setQuantity(
          new org.hl7.fhir.r4.model.Quantity()
              .setValue(d.getQuantity())
              .setUnit("{unit}")
              .setSystem(FhirSystems.UCUM)
              .setCode("{unit}"));
    }
    md.setWhenHandedOver(MapperSupport.date(d.getDispensedAt()));
    if (d.getPharmacistId() != null) {
      md.setPerformer(
          List.of(
              new MedicationDispense.MedicationDispensePerformerComponent()
                  .setActor(FhirIds.ref(ResourceType.Practitioner, d.getPharmacistId()))));
    }
    return md;
  }

  public ServiceRequest serviceRequest(LabOrder order, LabTest test) {
    ServiceRequest sr = new ServiceRequest();
    sr.setId(FhirIds.id(order.getId()));
    sr.setStatus(mapServiceRequestStatus(order.getStatus()));
    sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
    sr.setSubject(FhirIds.ref(ResourceType.Patient, order.getPatientId()));
    if (order.getEncounterId() != null) {
      sr.setEncounter(FhirIds.ref(ResourceType.Encounter, order.getEncounterId()));
    }
    sr.setAuthoredOn(MapperSupport.date(order.getOrderedAt()));
    if (order.getOrderType() == OrderType.DIRECT) {
      // Direct test → requester is the Patient (research / task requirement).
      sr.setRequester(FhirIds.ref(ResourceType.Patient, order.getPatientId()));
    } else if (order.getOrderedBy() != null) {
      sr.setRequester(FhirIds.ref(ResourceType.Practitioner, order.getOrderedBy()));
    }
    if (test != null) {
      if (test.getLoincCode() != null) {
        sr.setCode(MapperSupport.coding(FhirSystems.LOINC, test.getLoincCode(), test.getName()));
      } else {
        sr.setCode(MapperSupport.text(test.getName()));
      }
    }
    return sr;
  }

  public org.hl7.fhir.r4.model.DiagnosticReport diagnosticReport(
      DiagnosticReport report, LabOrder order, LabTest test) {
    org.hl7.fhir.r4.model.DiagnosticReport dr = new org.hl7.fhir.r4.model.DiagnosticReport();
    dr.setId(FhirIds.id(report.getId()));
    dr.setStatus(
        report.getVerifiedAt() == null
            ? org.hl7.fhir.r4.model.DiagnosticReport.DiagnosticReportStatus.PRELIMINARY
            : org.hl7.fhir.r4.model.DiagnosticReport.DiagnosticReportStatus.FINAL);
    if (order != null) {
      dr.setSubject(FhirIds.ref(ResourceType.Patient, order.getPatientId()));
      if (order.getEncounterId() != null) {
        dr.setEncounter(FhirIds.ref(ResourceType.Encounter, order.getEncounterId()));
      }
      dr.addBasedOn(FhirIds.ref(ResourceType.ServiceRequest, order.getId()));
    }
    if (test != null && test.getLoincCode() != null) {
      dr.setCode(MapperSupport.coding(FhirSystems.LOINC, test.getLoincCode(), test.getName()));
    } else if (test != null) {
      dr.setCode(MapperSupport.text(test.getName()));
    } else {
      dr.setCode(MapperSupport.text("Laboratory report"));
    }
    if (report.getVerifiedAt() != null) {
      dr.setIssued(MapperSupport.date(report.getVerifiedAt()));
    }
    if (report.getSummary() != null) {
      dr.setConclusion(report.getSummary());
    }
    if (report.getAbnormalFlag()) {
      dr.addConclusionCode(MapperSupport.text("abnormal"));
    }
    if (report.getCriticalFlag()) {
      dr.addConclusionCode(MapperSupport.text("critical"));
    }
    return dr;
  }

  public org.hl7.fhir.r4.model.Consent consent(Consent c) {
    org.hl7.fhir.r4.model.Consent out = new org.hl7.fhir.r4.model.Consent();
    out.setId(FhirIds.id(c.getId()));
    out.setStatus(
        c.getRevokedAt() != null || !c.getGranted()
            ? org.hl7.fhir.r4.model.Consent.ConsentState.INACTIVE
            : org.hl7.fhir.r4.model.Consent.ConsentState.ACTIVE);
    out.setPatient(FhirIds.ref(ResourceType.Patient, c.getPatientId()));
    out.setDateTime(MapperSupport.date(c.getGrantedAt()));
    out.setScope(
        MapperSupport.coding(
            "http://terminology.hl7.org/CodeSystem/consentscope", "patient-privacy", "Privacy"));
    out.addCategory(
        MapperSupport.coding(
            FhirSystems.PURPOSE_OF_USE,
            c.getConsentType() == null ? "TREAT" : c.getConsentType().name(),
            c.getConsentType() == null ? null : c.getConsentType().name()));
    if (c.getVersionLabel() != null) {
      out.setPolicyRule(MapperSupport.text(c.getVersionLabel()));
    }
    return out;
  }

  public org.hl7.fhir.r4.model.AuditEvent auditEvent(AuditEvent e) {
    org.hl7.fhir.r4.model.AuditEvent ae = new org.hl7.fhir.r4.model.AuditEvent();
    ae.setId(FhirIds.id(e.getId()));
    ae.setType(
        new Coding()
            .setSystem(FhirSystems.AUDIT_EVENT_TYPE)
            .setCode("rest")
            .setDisplay("RESTful Operation"));
    ae.addSubtype(new Coding().setCode(e.getAction()).setDisplay(e.getAction()));
    ae.setAction(AuditEventAction.R);
    ae.setRecorded(MapperSupport.date(e.getOccurredAt()));
    AuditEventAgentComponent agent = ae.addAgent();
    agent.setRequestor(true);
    if (e.getActorUserId() != null) {
      agent.setWho(new Reference("Practitioner/" + e.getActorUserId()));
    }
    if (e.getPatientId() != null || e.getResourceType() != null) {
      AuditEventEntityComponent entity = ae.addEntity();
      if (e.getResourceType() != null && e.getResourceId() != null) {
        entity.setWhat(new Reference(e.getResourceType() + "/" + e.getResourceId()));
      } else if (e.getPatientId() != null) {
        entity.setWhat(FhirIds.ref(ResourceType.Patient, e.getPatientId()));
      }
      if (e.getDetails() != null) {
        entity.setDescription(e.getDetails());
      }
    }
    return ae;
  }

  public Provenance provenance(AuditEvent e) {
    Provenance p = new Provenance();
    p.setId("prov-" + FhirIds.id(e.getId()));
    p.setRecorded(MapperSupport.date(e.getOccurredAt()));
    if (e.getResourceType() != null && e.getResourceId() != null) {
      p.addTarget(new Reference(e.getResourceType() + "/" + e.getResourceId()));
    } else if (e.getPatientId() != null) {
      p.addTarget(FhirIds.ref(ResourceType.Patient, e.getPatientId()));
    }
    Provenance.ProvenanceAgentComponent agent = p.addAgent();
    agent.setType(MapperSupport.text(e.getAction()));
    if (e.getActorUserId() != null) {
      agent.setWho(new Reference("Practitioner/" + e.getActorUserId()));
    }
    return p;
  }

  public Communication communication(Notification n) {
    Communication c = new Communication();
    c.setId(FhirIds.id(n.getId()));
    c.setStatus(mapCommunicationStatus(n.getStatus() == null ? null : n.getStatus().name()));
    if (n.getPatientId() != null) {
      c.setSubject(FhirIds.ref(ResourceType.Patient, n.getPatientId()));
    }
    if (n.getRecipientUserId() != null) {
      c.addRecipient(new Reference("Practitioner/" + n.getRecipientUserId()));
    }
    c.setSent(MapperSupport.date(n.getSentAt() == null ? n.getCreatedAt() : n.getSentAt()));
    if (n.getSubject() != null || n.getBody() != null) {
      Communication.CommunicationPayloadComponent payload = c.addPayload();
      String text =
          (n.getSubject() == null ? "" : n.getSubject() + "\n")
              + (n.getBody() == null ? "" : n.getBody());
      payload.setContent(new StringType(text.strip()));
    }
    if (n.getChannel() != null) {
      c.addMedium(MapperSupport.text(n.getChannel()));
    }
    return c;
  }

  public Task task(Reminder r) {
    Task t = new Task();
    t.setId(FhirIds.id(r.getId()));
    t.setStatus(mapTaskStatus(r.getStatus()));
    t.setIntent(Task.TaskIntent.ORDER);
    t.setFor(FhirIds.ref(ResourceType.Patient, r.getPatientId()));
    t.setExecutionPeriod(new Period().setStart(MapperSupport.date(r.getScheduledAt())));
    t.setDescription(r.getReminderType());
    if (r.getPayload() != null) {
      t.setNote(List.of(new Annotation().setText(r.getPayload())));
    }
    if (r.getChannel() != null) {
      t.addInput()
          .setType(MapperSupport.text("channel"))
          .setValue(new StringType(r.getChannel()));
    }
    return t;
  }

  private static Enumerations.AdministrativeGender mapGender(String sex) {
    if (sex == null) {
      return Enumerations.AdministrativeGender.UNKNOWN;
    }
    return switch (sex.trim().toUpperCase()) {
      case "M", "MALE" -> Enumerations.AdministrativeGender.MALE;
      case "F", "FEMALE" -> Enumerations.AdministrativeGender.FEMALE;
      case "O", "OTHER" -> Enumerations.AdministrativeGender.OTHER;
      default -> Enumerations.AdministrativeGender.UNKNOWN;
    };
  }

  private static org.hl7.fhir.r4.model.Appointment.AppointmentStatus mapAppointmentStatus(
      AppointmentStatus status) {
    if (status == null) {
      return org.hl7.fhir.r4.model.Appointment.AppointmentStatus.PENDING;
    }
    return switch (status) {
      case BOOKED -> org.hl7.fhir.r4.model.Appointment.AppointmentStatus.BOOKED;
      case CHECKED_IN, IN_PROGRESS -> org.hl7.fhir.r4.model.Appointment.AppointmentStatus.ARRIVED;
      case COMPLETED -> org.hl7.fhir.r4.model.Appointment.AppointmentStatus.FULFILLED;
      case CANCELLED -> org.hl7.fhir.r4.model.Appointment.AppointmentStatus.CANCELLED;
      case NO_SHOW -> org.hl7.fhir.r4.model.Appointment.AppointmentStatus.NOSHOW;
    };
  }

  private static org.hl7.fhir.r4.model.Encounter.EncounterStatus mapEncounterStatus(
      EncounterStatus status) {
    if (status == null) {
      return org.hl7.fhir.r4.model.Encounter.EncounterStatus.UNKNOWN;
    }
    return switch (status) {
      case IN_PROGRESS -> org.hl7.fhir.r4.model.Encounter.EncounterStatus.INPROGRESS;
      case COMPLETED -> org.hl7.fhir.r4.model.Encounter.EncounterStatus.FINISHED;
      case CANCELLED -> org.hl7.fhir.r4.model.Encounter.EncounterStatus.CANCELLED;
    };
  }

  private static MedicationRequest.MedicationRequestStatus mapMedReqStatus(
      PrescriptionStatus status) {
    if (status == null) {
      return MedicationRequest.MedicationRequestStatus.UNKNOWN;
    }
    return switch (status) {
      case DRAFT -> MedicationRequest.MedicationRequestStatus.DRAFT;
      case SIGNED, AMENDED -> MedicationRequest.MedicationRequestStatus.ACTIVE;
      case ENTERED_IN_ERROR -> MedicationRequest.MedicationRequestStatus.ENTEREDINERROR;
    };
  }

  private static ServiceRequest.ServiceRequestStatus mapServiceRequestStatus(LabOrderStatus s) {
    if (s == null) {
      return ServiceRequest.ServiceRequestStatus.UNKNOWN;
    }
    return switch (s) {
      case ORDERED, SAMPLE_COLLECTED, IN_LAB -> ServiceRequest.ServiceRequestStatus.ACTIVE;
      case RESULTED, VERIFIED -> ServiceRequest.ServiceRequestStatus.COMPLETED;
      case CANCELLED -> ServiceRequest.ServiceRequestStatus.REVOKED;
    };
  }

  private static Communication.CommunicationStatus mapCommunicationStatus(String status) {
    if (status == null) {
      return Communication.CommunicationStatus.UNKNOWN;
    }
    return switch (status) {
      case "SENT" -> Communication.CommunicationStatus.COMPLETED;
      case "FAILED" -> Communication.CommunicationStatus.ENTEREDINERROR;
      default -> Communication.CommunicationStatus.INPROGRESS;
    };
  }

  private static Task.TaskStatus mapTaskStatus(ReminderStatus status) {
    if (status == null) {
      return Task.TaskStatus.DRAFT;
    }
    return switch (status) {
      case PENDING -> Task.TaskStatus.REQUESTED;
      case SENT -> Task.TaskStatus.COMPLETED;
      case FAILED -> Task.TaskStatus.FAILED;
      case CANCELLED -> Task.TaskStatus.CANCELLED;
    };
  }
}
