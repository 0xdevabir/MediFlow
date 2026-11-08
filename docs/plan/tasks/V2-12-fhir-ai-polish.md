# V2-12 — FHIR config + AI service dictionary alignment

**Wave:** v2-W1  
**Branch:** `feat/V2-12-fhir-ai-polish`  
**Depends on (by name):** V2-01 yml property `mediflow.fhir.base-url`; V2-09 dictionary export contract

## Owns

- `backend/src/main/java/com/mediflow/fhir/**`
- `backend/src/main/resources/messages/fhir_en.properties`
- `backend/src/main/resources/messages/fhir_bn.properties`
- `ai-service/**` (Python package, configs, dictionary client — **not** `infra/**`, **not** Docker files)
- Flyway `V760__*` … `V769__*` (only if FHIR tables need tweak; prefer zero migrations)

## Build

1. **FHIR:** make `FhirSystems` (or equivalent) read `mediflow.fhir.base-url` from Spring config instead of a hard-coded constant; keep R4 mappings for Patient, Encounter, Flag/Red Alert, Observation, MedicationRequest, ServiceRequest/DiagnosticReport (direct vs doctor), AuditEvent.
2. Ensure direct-lab ServiceRequest requester = patient still holds.
3. **AI service:** confirm dictionary HTTP/file loader matches pharmacy export; tolerate empty/illustrative; no PHI in logs (`_NoPhiFilter` preserved).
4. Align any field names with shared `DrugDictionaryPort` export JSON if V2-09 changes columns — adapt Python side only.
5. Do **not** add tests, Docker, or infra compose changes.
6. Short `docs/plan/reports/V2-12.md` when done (what changed).

## Acceptance

- Changing `mediflow.fhir.base-url` changes system URLs used in FHIR resources.
- AI dictionary reload still works against backend export.
- No edits to `shared/`, `pom.xml`, or other modules’ Java packages.
