package com.mediflow.fhir.service;

import com.mediflow.fhir.FhirSystems;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.RestfulCapabilityMode;
import org.hl7.fhir.r4.model.CapabilityStatement.TypeRestfulInteraction;
import org.hl7.fhir.r4.model.Enumerations;
import org.springframework.stereotype.Component;

@Component
public class CapabilityStatementFactory {

  private final FhirSystems systems;

  public CapabilityStatementFactory(FhirSystems systems) {
    this.systems = systems;
  }

  private static final List<String> RESOURCES =
      List.of(
          "Patient",
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
          "Task");

  public CapabilityStatement build() {
    CapabilityStatement cs = new CapabilityStatement();
    cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
    cs.setDate(new Date());
    cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
    cs.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
    cs.addFormat("application/fhir+json");
    cs.addFormat("application/json");
    cs.setSoftware(
        new CapabilityStatement.CapabilityStatementSoftwareComponent()
            .setName("MediFlow AI FHIR R4 facade")
            .setVersion("T12"));
    cs.setImplementation(
        new CapabilityStatement.CapabilityStatementImplementationComponent()
            .setDescription("Read-only HL7 FHIR R4 facade")
            .setUrl(systems.fhirBase()));

    CapabilityStatementRestComponent rest = cs.addRest();
    rest.setMode(RestfulCapabilityMode.SERVER);
    rest.getSecurity()
        .setCors(false)
        .addService()
        .addCoding()
        .setSystem("http://terminology.hl7.org/CodeSystem/restful-security-service")
        .setCode("OAuth")
        .setDisplay("OAuth");
    rest.getSecurity()
        .setDescription(
            "Session cookie (patient-consented) or OAuth2 client-credentials Bearer token;"
                + " FHIR_SHARE consent required for machine clients.");

    for (String type : RESOURCES) {
      CapabilityStatementRestResourceComponent res = rest.addResource();
      res.setType(type);
      res.addInteraction().setCode(TypeRestfulInteraction.READ);
      res.setVersioning(CapabilityStatement.ResourceVersionPolicy.NOVERSION);
      if ("Observation".equals(type)) {
        res.addSupportedProfile(FhirSystems.VITAL_SIGNS_PROFILE);
      }
      if ("Patient".equals(type)) {
        res.addOperation()
            .setName("everything")
            .setDefinition("http://hl7.org/fhir/OperationDefinition/Patient-everything");
      }
    }
    return cs;
  }
}
