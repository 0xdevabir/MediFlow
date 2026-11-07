package com.mediflow.fhir;

/** Terminology and identifier system URIs used by the FHIR R4 facade (DOCX Table 10–11). */
public final class FhirSystems {

  public static final String FHIR_BASE = "http://localhost/fhir/r4";

  public static final String LOINC = "http://loinc.org";
  public static final String UCUM = "http://unitsofmeasure.org";
  public static final String ICD10 = "http://hl7.org/fhir/sid/icd-10";
  public static final String RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";
  public static final String ATC = "http://www.whocc.no/atc";
  public static final String SNOMED = "http://snomed.info/sct";

  /** Bangladesh DGDA brand registration number (local extension). */
  public static final String DGDA_BRAND =
      "https://mediflow.local/fhir/StructureDefinition/dgda-brand-reg-no";

  public static final String MRN = "https://mediflow.local/fhir/sid/mrn";
  public static final String NATIONAL_ID = "https://mediflow.local/fhir/sid/national-id";
  public static final String LICENCE = "https://mediflow.local/fhir/sid/practitioner-licence";

  public static final String VITAL_SIGNS_CATEGORY =
      "http://terminology.hl7.org/CodeSystem/observation-category";
  public static final String VITAL_SIGNS_PROFILE =
      "http://hl7.org/fhir/StructureDefinition/vitalsigns";

  public static final String AUDIT_EVENT_TYPE =
      "http://terminology.hl7.org/CodeSystem/audit-event-type";
  public static final String PURPOSE_OF_USE =
      "http://terminology.hl7.org/CodeSystem/v3-ActReason";

  private FhirSystems() {}
}
