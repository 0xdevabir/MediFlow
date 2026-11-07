# T12 — HL7 FHIR R4 facade (Wave 2)

Run after Wave 1 is merged. Read PLAN + CONVENTIONS. Research: DOCX §9 Tables 10–11; PDF §10 mappings.

## Owns
`com.mediflow.fhir/**`, V600–V649.

## Build
1. Mappers (HAPI FHIR R4 structures) for: Patient, Organization/Location (hospital), Practitioner/PractitionerRole, Appointment/Schedule/Slot, Encounter (incl. emergency class for Red Alert) + Flag, Observation (vital-signs profile, LOINC + UCUM), Condition (ICD-10), AllergyIntolerance, MedicationRequest (RxNorm/ATC + DGDA brand extension), MedicationDispense, ServiceRequest (direct test → requester = Patient), DiagnosticReport, Consent, AuditEvent/Provenance, Communication/Task.
2. Read-only REST `/fhir/r4/{Type}/{id}` and `/fhir/r4/Patient/{id}/$everything` (IPS-style summary bundle), JSON, OAuth2 client-credentials or patient-consented session; every call audited; RBAC + consent enforced.
3. `CapabilityStatement` at `/fhir/r4/metadata`.
4. Validate output against R4 base profiles with HAPI validator in tests.

## Acceptance
- Round-trip tests per resource; validator errors = 0; unauthorised access blocked.
