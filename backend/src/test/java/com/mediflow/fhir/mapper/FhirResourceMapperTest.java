package com.mediflow.fhir.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.mediflow.fhir.validate.R4ResourceValidator;
import com.mediflow.shared.domain.Allergy;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.ConditionDx;
import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.ConsentType;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.EncounterStatus;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.OrderType;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.RedAlert;
import com.mediflow.shared.domain.ReviewStatus;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.domain.VitalClass;
import com.mediflow.shared.domain.VitalObservation;
import com.mediflow.shared.domain.DrugBrand;
import com.mediflow.shared.domain.DrugGeneric;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FhirResourceMapperTest {

  private FhirResourceMapper mapper;
  private R4ResourceValidator validator;
  private IParser parser;

  @BeforeEach
  void setUp() {
    mapper = new FhirResourceMapper();
    FhirContext ctx = FhirContext.forR4Cached();
    parser = ctx.newJsonParser();
    validator = new R4ResourceValidator(ctx, parser);
  }

  @Test
  void patientRoundTripAndValid() {
    Patient p = new Patient();
    p.setId(UUID.randomUUID());
    p.setMrn("MRN-1");
    p.setGivenName("Amina");
    p.setFamilyName("Rahman");
    p.setDateOfBirth(LocalDate.of(1990, 5, 1));
    p.setSex("F");
    assertRoundTrip(mapper.patient(p));
  }

  @Test
  void organizationAndLocationRoundTrip() {
    Hospital h = hospital();
    assertRoundTrip(mapper.organization(h));
    assertRoundTrip(mapper.location(h));
  }

  @Test
  void practitionerRoundTrip() {
    Practitioner pr = new Practitioner();
    pr.setId(UUID.randomUUID());
    pr.setName("Dr Karim");
    pr.setLicenceNo("BMDC-1");
    pr.setRoleLabel("Doctor");
    assertRoundTrip(mapper.practitioner(pr));
  }

  @Test
  void encounterEmergencyClassAndFlag() {
    Encounter e = new Encounter();
    e.setId(UUID.randomUUID());
    e.setPatientId(UUID.randomUUID());
    e.setHospitalId(UUID.randomUUID());
    e.setStatus(EncounterStatus.IN_PROGRESS);
    e.setStartedAt(Instant.parse("2026-01-01T10:00:00Z"));
    var enc = mapper.encounter(e, true);
    assertEquals("EMER", enc.getClass_().getCode());
    assertRoundTrip(enc);

    RedAlert alert = new RedAlert();
    alert.setId(UUID.randomUUID());
    alert.setPatientId(e.getPatientId());
    alert.setHospitalId(e.getHospitalId());
    alert.setEncounterId(e.getId());
    alert.setLevel(TriageLevel.CRITICAL);
    alert.setReason("shock");
    alert.setRaisedAt(Instant.parse("2026-01-01T10:05:00Z"));
    assertRoundTrip(mapper.redAlertFlag(alert));
  }

  @Test
  void observationVitalSignsProfile() {
    VitalObservation v = new VitalObservation();
    v.setId(UUID.randomUUID());
    v.setPatientId(UUID.randomUUID());
    v.setEncounterId(UUID.randomUUID());
    v.setVitalType("HR");
    v.setValueNumeric(new BigDecimal("88"));
    v.setUnit("/min");
    v.setClassification(VitalClass.NORMAL);
    v.setRecordedAt(Instant.parse("2026-01-01T11:00:00Z"));
    var obs = mapper.observation(v);
    assertTrue(obs.getMeta().getProfile().stream().anyMatch(u -> u.getValue().contains("vitalsigns")));
    assertEquals("8867-4", obs.getCode().getCodingFirstRep().getCode());
    assertRoundTrip(obs);
  }

  @Test
  void bloodPressureComponents() {
    VitalObservation v = new VitalObservation();
    v.setId(UUID.randomUUID());
    v.setPatientId(UUID.randomUUID());
    v.setEncounterId(UUID.randomUUID());
    v.setVitalType("BP");
    v.setValueText("120/80");
    v.setUnit("mm[Hg]");
    v.setClassification(VitalClass.NORMAL);
    v.setRecordedAt(Instant.now());
    var obs = mapper.observation(v);
    assertEquals(2, obs.getComponent().size());
    assertRoundTrip(obs);
  }

  @Test
  void conditionAllergyAppointment() {
    ConditionDx dx = new ConditionDx();
    dx.setId(UUID.randomUUID());
    dx.setPatientId(UUID.randomUUID());
    dx.setEncounterId(UUID.randomUUID());
    dx.setIcd10Code("J06.9");
    dx.setDisplay("URI");
    dx.setClinicalStatus("active");
    dx.setRecordedAt(Instant.now());
    assertRoundTrip(mapper.condition(dx));

    Allergy a = new Allergy();
    a.setId(UUID.randomUUID());
    a.setPatientId(dx.getPatientId());
    a.setSubstance("Penicillin");
    a.setSubstanceCode("7980");
    a.setCriticality("high");
    assertRoundTrip(mapper.allergy(a));

    Appointment ap = new Appointment();
    ap.setId(UUID.randomUUID());
    ap.setPatientId(dx.getPatientId());
    ap.setDoctorId(UUID.randomUUID());
    ap.setHospitalId(UUID.randomUUID());
    ap.setStatus(AppointmentStatus.BOOKED);
    ap.setBookedAt(Instant.now());
    assertRoundTrip(mapper.appointment(ap));
  }

  @Test
  void medicationRequestIncludesDgdaExtension() {
    Prescription rx = new Prescription();
    rx.setId(UUID.randomUUID());
    rx.setPatientId(UUID.randomUUID());
    rx.setEncounterId(UUID.randomUUID());
    rx.setStatus(PrescriptionStatus.SIGNED);
    rx.setSignedAt(Instant.now());
    rx.setSignedBy(UUID.randomUUID());

    PrescriptionItem item = new PrescriptionItem();
    item.setId(UUID.randomUUID());
    item.setPrescriptionId(rx.getId());
    item.setGenericId(UUID.randomUUID());
    item.setBrandId(UUID.randomUUID());
    item.setDose("500 mg");
    item.setRoute("PO");
    item.setFrequency("BID");
    item.setDuration("5d");
    item.setReviewStatus(ReviewStatus.ACCEPTED);

    DrugGeneric generic = new DrugGeneric();
    generic.setId(item.getGenericId());
    generic.setName("Amoxicillin");
    generic.setAtcCode("J01CA04");
    generic.setRxnormCode("723");

    DrugBrand brand = new DrugBrand();
    brand.setId(item.getBrandId());
    brand.setName("Moxacil");
    brand.setDgdaRegNo("DGDA-123");
    brand.setGenericId(generic.getId());

    var mr = mapper.medicationRequest(rx, item, generic, brand);
    assertTrue(
        mr.getExtension().stream()
            .anyMatch(e -> e.getUrl().contains("dgda-brand-reg-no")));
    assertRoundTrip(mr);
  }

  @Test
  void directLabOrderRequesterIsPatient() {
    LabOrder order = new LabOrder();
    order.setId(UUID.randomUUID());
    order.setPatientId(UUID.randomUUID());
    order.setHospitalId(UUID.randomUUID());
    order.setOrderType(OrderType.DIRECT);
    order.setStatus(LabOrderStatus.ORDERED);
    order.setOrderedAt(Instant.now());
    LabTest test = new LabTest();
    test.setId(UUID.randomUUID());
    test.setCode("CBC");
    test.setName("Complete blood count");
    test.setLoincCode("58410-2");
    var sr = mapper.serviceRequest(order, test);
    assertEquals("Patient/" + order.getPatientId(), sr.getRequester().getReference());
    assertRoundTrip(sr);
  }

  @Test
  void consentRoundTrip() {
    Consent c = new Consent();
    c.setId(UUID.randomUUID());
    c.setPatientId(UUID.randomUUID());
    c.setConsentType(ConsentType.FHIR_SHARE);
    c.setGranted(true);
    c.setVersionLabel("v1");
    c.setGrantedAt(Instant.now());
    assertRoundTrip(mapper.consent(c));
  }

  @Test
  void allMappedResourcesHaveZeroValidatorErrors() {
    List<Function<FhirResourceMapper, Resource>> factories =
        List.of(
            m -> {
              Patient p = new Patient();
              p.setId(UUID.randomUUID());
              p.setGivenName("A");
              p.setFamilyName("B");
              return m.patient(p);
            },
            m -> m.organization(hospital()));
    for (var f : factories) {
      assertRoundTrip(f.apply(mapper));
    }
  }

  private Hospital hospital() {
    Hospital h = new Hospital();
    h.setId(UUID.randomUUID());
    h.setName("Dhaka General");
    h.setType("hospital");
    h.setActive(true);
    h.setAddress("Dhaka");
    h.setPhone("01700000000");
    return h;
  }

  private void assertRoundTrip(Resource resource) {
    String json = parser.encodeResourceToString(resource);
    Resource parsed = (Resource) parser.parseResource(json);
    assertEquals(resource.getResourceType(), parsed.getResourceType());
    List<String> errors = validator.validate(resource);
    assertEquals(List.of(), errors, () -> "validator errors for " + resource.getResourceType());
  }
}
