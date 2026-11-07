# Conventions — read before writing code

## Ownership & conflicts
- Each task owns: `backend/src/main/java/com/mediflow/<module>/**`, `backend/src/test/java/com/mediflow/<module>/**`, `templates/<module>/**`, `messages/<module>_{en,bn}.properties`, and its Flyway range.
- `shared/**`, `pom.xml`, `application*.yml`, `templates/layout/**`, `V1__baseline.sql` belong to **T00**. Wave-1 agents must not edit them; file a request in `docs/plan/requests/<task>.md`.
- Exception: a task may add **one** `application-<module>.yml` profile fragment and its own dependencies via a request file (T00 pre-adds all known deps).

## Flyway ranges (all `out-of-order: true` in dev)
| Owner | Range |
|---|---|
| T00 baseline | V1 – V99 |
| T01 identity | V100 – V149 |
| T02 patient | V150 – V199 |
| T03 directory | V200 – V249 |
| T04 appointment | V250 – V299 |
| T05 clinical | V300 – V349 |
| T06 prescription | V350 – V399 |
| T07 lab | V400 – V449 |
| T08 pharmacy | V450 – V499 (DGDA seed data) |
| T10 notification | V500 – V549 |
| T11 audit/admin | V550 – V599 |
| T12 fhir | V600 – V649 |

## Code style
- Layers per module: `web` (Thymeleaf controllers) → `service` (transactional, `@PreAuthorize`) → `domain` (entities from T00) → `repo`.
- REST JSON endpoints (only where needed: SSE, AI callbacks, FHIR) under `/api/**`; HTML under the module path.
- Cross-module: call **only** the port interfaces in `com.mediflow.shared.port`, or publish/listen to events in `com.mediflow.shared.event` (`@TransactionalEventListener`). Never inject another module's service/repo.
- Every service method that reads/writes clinical data calls `AuditPort.record(...)` (T11 implements; T00 provides a logging stub).
- Enums for statuses/levels live in `shared.domain`.
- All user-visible strings via `#{...}` message keys; provide `en` and `bn`.
- No secrets in code; config via env vars (`MEDIFLOW_*`).
- Tests: unit tests for logic; `@SpringBootTest` + Testcontainers (Postgres, RabbitMQ) for integration; `@WithMockUser`-style tests for each `@PreAuthorize`.

## Roles (Spring authorities)
`ROLE_PATIENT, ROLE_DOCTOR, ROLE_NURSE, ROLE_FRONT_DESK, ROLE_LAB_TECH, ROLE_PHARMACIST, ROLE_ER, ROLE_ADMIN, ROLE_CLINICAL_APPROVER`

## Key shared events (T00 defines, owners publish)
| Event | Publisher | Typical listeners |
|---|---|---|
| `PatientRegistered` | T02 | T10 |
| `AppointmentBooked / Cancelled / CheckedIn` | T04 | T10, T05 |
| `TriageLevelSet(level)` | T04 | T04 RedAlert |
| `VitalsClassified(patientLevel)` | T05 | T04 (CRITICAL → Red Alert) |
| `RedAlertRaised` | T04 | T10 |
| `LabOrderCreated / LabReportVerified(abnormal, critical)` | T07 | T05, T10 |
| `PrescriptionSigned / Revoked / Amended` | T06 | T08, T10 |
| `MedicationDispensed` | T08 | T06, T10 |
| `AiJobCompleted / AiJobFailed` | T06 (from AI queue) | T06 SSE |

## AI service contract (T06 ↔ T09)
- Request queue `ai.rx.requests`, reply queue `ai.rx.results`, DLQ `ai.rx.dlq`.
- Request: `{jobId, encounterId, inputType: "IMAGE"|"TEXT", objectKey?, text?, locale}` — **no patient identifiers**.
- Result: `{jobId, status: "OK"|"UNREADABLE"|"ERROR", items:[{raw, brand?, generic?, atc?, strength?, form?, dose, route, frequency, duration, conf:{drug,dose,frequency,route,duration}}], timingsMs:{...}, modelVersion}`
- Java side (T06) computes item confidence, runs the contradiction engine (it has the patient context) and applies the 85 % gate.
