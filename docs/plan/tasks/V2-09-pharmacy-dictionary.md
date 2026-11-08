# V2-09 — Pharmacy: shared DrugDictionaryPort + events

**Wave:** v2-W1  
**Branch:** `feat/V2-09-pharmacy-dictionary`  
**Depends on (by name):** V2-01 `DrugDictionaryPort`, `PatientQrPort`

## Owns

- `backend/src/main/java/com/mediflow/pharmacy/**`
- `backend/src/main/resources/templates/pharmacy/**`
- `backend/src/main/resources/messages/pharmacy_en.properties`
- `backend/src/main/resources/messages/pharmacy_bn.properties`
- `backend/src/main/resources/application-pharmacy.yml`
- Flyway `V730__*` … `V739__*`

## Build

1. Move interface: pharmacy’s `DrugDictionaryPort` type becomes **`com.mediflow.shared.port.DrugDictionaryPort`** (delete local interface file; keep `DrugDictionaryService` implementing the shared interface).
2. Export endpoint for AI hourly refresh remains; ensure JSON matches `ai-service` expectations.
3. Replace local `PharmacyEvents` publications with shared `DomainEvents` equivalents where defined (`MedicationDispensed`, etc.); if event missing, file note for V2-01 — do not edit shared unless you must no-op wait: **cannot edit shared** — use existing shared events only; if gap, keep local event **and** document request for V2-01 follow-up PR.
4. Dispense / stock / substitution / dictionary admin UI → design system.
5. Use `PatientQrPort` for patient identity at dispense when available.
6. Pharmacist home fragment: dispense queue + low stock; EN+BN.
7. Mark placeholder interaction/dose seeds clearly in admin UI (licensed source still required for production clinically).

## Acceptance

- `DrugDictionaryService` is the Spring bean for `com.mediflow.shared.port.DrugDictionaryPort`.
- Dictionary search/import/dispense still work; AI export URL unchanged in spirit.
- No edits outside Owns.
