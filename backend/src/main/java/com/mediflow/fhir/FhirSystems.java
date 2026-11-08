package com.mediflow.fhir;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FHIR terminology / identifier systems. External standards (LOINC, UCUM, …) are fixed; MediFlow
 * local systems and the CapabilityStatement base URL are derived from {@code
 * mediflow.fhir.base-url}.
 */
@Component
public class FhirSystems {

  public static final String LOINC = "http://loinc.org";
  public static final String UCUM = "http://unitsofmeasure.org";
  public static final String ICD10 = "http://hl7.org/fhir/sid/icd-10";
  public static final String RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";
  public static final String ATC = "http://www.whocc.no/atc";
  public static final String SNOMED = "http://snomed.info/sct";

  public static final String VITAL_SIGNS_CATEGORY =
      "http://terminology.hl7.org/CodeSystem/observation-category";
  public static final String VITAL_SIGNS_PROFILE =
      "http://hl7.org/fhir/StructureDefinition/vitalsigns";

  public static final String AUDIT_EVENT_TYPE =
      "http://terminology.hl7.org/CodeSystem/audit-event-type";
  public static final String PURPOSE_OF_USE =
      "http://terminology.hl7.org/CodeSystem/v3-ActReason";

  private final String fhirBase;
  private final String mrn;
  private final String nationalId;
  private final String licence;
  private final String dgdaBrand;
  private final String dgdaBrandName;
  private final String redAlert;

  public FhirSystems(
      @Value("${mediflow.fhir.base-url:http://localhost/fhir/r4}") String baseUrl) {
    this.fhirBase = normalizeBase(baseUrl);
    String root = localRoot(this.fhirBase);
    this.mrn = root + "/sid/mrn";
    this.nationalId = root + "/sid/national-id";
    this.licence = root + "/sid/practitioner-licence";
    this.dgdaBrand = root + "/StructureDefinition/dgda-brand-reg-no";
    this.dgdaBrandName = root + "/StructureDefinition/dgda-brand-name";
    this.redAlert = root + "/CodeSystem/red-alert";
  }

  /** CapabilityStatement {@code implementation.url} and absolute Bundle fullUrls. */
  public String fhirBase() {
    return fhirBase;
  }

  public String mrn() {
    return mrn;
  }

  public String nationalId() {
    return nationalId;
  }

  public String licence() {
    return licence;
  }

  public String dgdaBrand() {
    return dgdaBrand;
  }

  public String dgdaBrandName() {
    return dgdaBrandName;
  }

  public String redAlert() {
    return redAlert;
  }

  static String normalizeBase(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://localhost/fhir/r4";
    }
    String trimmed = baseUrl.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  /**
   * Local identifier / extension root: strip a trailing {@code /r4} so {@code
   * http://host/fhir/r4} → {@code http://host/fhir}.
   */
  static String localRoot(String fhirBase) {
    if (fhirBase.endsWith("/r4")) {
      return fhirBase.substring(0, fhirBase.length() - 3);
    }
    return fhirBase;
  }
}
