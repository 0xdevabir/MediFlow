# V2-06 — Clinical consultation & vitals completion

**Wave:** v2-W1  
**Branch:** `feat/V2-06-clinical-consultation`  
**Depends on (by name):** V2-01 `LabOrderPort` (shared); V2-08 implements real bean; Chart.js webjar from V2-01 pom if added

## Owns

- `backend/src/main/java/com/mediflow/clinical/**`
- `backend/src/main/resources/templates/clinical/**`
- `backend/src/main/resources/messages/clinical_en.properties`
- `backend/src/main/resources/messages/clinical_bn.properties`
- `backend/src/main/resources/application-clinical.yml` if present / clinical message extras under `resources/clinical/**`
- Flyway `V700__*` … `V709__*`

## Build

1. **Remove clinical-local `LabOrderPort` / `NoOpLabOrderPort` / config bean**; inject `com.mediflow.shared.port.LabOrderPort` only. After V2-08 merge, `orderLab` must persist a real order id (non-zero UUID).
2. Consultation UI: lab order form — pick from directory/lab test codes if a read API is available without owning lab; else keep code entry but show returned order id + link to `/lab/...` encounter orders when V2-08 routes exist.
3. Deep link to prescriptions: ensure consult “Prescribe” uses `/prescriptions?encounterId=` (V2-07 honors it).
4. Add **follow-up date** on encounter/note (Flyway in V700 range) and publish `DomainEvents.FollowUpScheduled` when saved.
5. Bridge clinical access checks toward `@access` / `PatientAccessPort` where local `ClinicalAccess` duplicates policy.
6. Vitals trend: Chart.js (if webjar present) or CSS sparkline; keep polymorphic vitals invariants.
7. Doctor home fragment: open encounters today; nurse fragment: vitals pending.
8. Design-system pass on consultation / vitals / summary templates; EN+BN.

## Acceptance

- With V2-08 present, ordering a lab from consultation creates a visible lab order (id ≠ nil).
- Follow-up save emits `FollowUpScheduled`.
- Clinical package no longer defines its own `LabOrderPort` type.
- Templates match shared layout; safety invariants unchanged.
