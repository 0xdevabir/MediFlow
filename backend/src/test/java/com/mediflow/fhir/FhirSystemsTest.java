package com.mediflow.fhir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FhirSystemsTest {

  @Test
  void defaultBaseDerivesLocalIdentifierSystems() {
    FhirSystems systems = new FhirSystems("http://localhost/fhir/r4");
    assertEquals("http://localhost/fhir/r4", systems.fhirBase());
    assertEquals("http://localhost/fhir/sid/mrn", systems.mrn());
    assertEquals("http://localhost/fhir/sid/national-id", systems.nationalId());
    assertEquals("http://localhost/fhir/sid/practitioner-licence", systems.licence());
    assertEquals(
        "http://localhost/fhir/StructureDefinition/dgda-brand-reg-no", systems.dgdaBrand());
    assertEquals(
        "http://localhost/fhir/StructureDefinition/dgda-brand-name", systems.dgdaBrandName());
    assertEquals("http://localhost/fhir/CodeSystem/red-alert", systems.redAlert());
  }

  @Test
  void changingBaseUrlChangesSystemUrls() {
    FhirSystems systems = new FhirSystems("https://fhir.mediflow.bd/r4/");
    assertEquals("https://fhir.mediflow.bd/r4", systems.fhirBase());
    assertEquals("https://fhir.mediflow.bd/sid/mrn", systems.mrn());
  }

  @Test
  void blankFallsBackToDefault() {
    FhirSystems systems = new FhirSystems("  ");
    assertEquals("http://localhost/fhir/r4", systems.fhirBase());
  }
}
