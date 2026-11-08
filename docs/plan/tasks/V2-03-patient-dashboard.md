# V2-03 — Patient experience: dashboard, adherence, QR port

**Wave:** v2-W1  
**Branch:** `feat/V2-03-patient-dashboard`  
**Depends on (by name):** V2-01 `AdherenceQueryPort`, `PatientQrPort`; V2-10 implements adherence; V2-01 home may include patient fragment

## Owns

- `backend/src/main/java/com/mediflow/patient/**`
- `backend/src/main/resources/templates/patient/**`
- `backend/src/main/resources/messages/patient_en.properties`
- `backend/src/main/resources/messages/patient_bn.properties`
- Flyway `V670__*` … `V679__*`

## Build

1. **Patient landing:** role redirect on `GET /patients` (patient → me/dashboard; front desk → search) if not already clean.
2. **Dashboard / profile:** include adherence widget via `th:replace="~{notification/fragments :: adherence}"` **or** a patient-local wrapper that loads `AdherenceQueryPort.adherencePercent` into `pct` model attribute (preferred if fragment expects `pct`). Do not edit notification templates — only consume the fragment contract; if fragment needs different model names, adapt in the patient controller.
3. Implement **`PatientQrPort`** bean using existing QR/token TTL (`mediflow.patient.qr-token-ttl`) for pharmacy/lab identity.
4. Patient-facing shortcuts: next appointment, book (`/directory` / appointments), direct lab, prescriptions, lab reports, reminders — use V2-01 `page-shell` / role tiles.
5. Consent adapter: if dual consent paths remain, mark `@Primary` on the shared-table gateway or document single path in report.
6. Polish register/profile/card/search with design system; mobile-friendly patient card / QR display.
7. Optional home fragment: `patient/fragments :: dashboard` for `HomeController` include.
8. EN+BN for all new keys.

## Acceptance

- Logged-in PATIENT opening profile/home sees adherence percent (or “no doses due”) backed by `AdherenceQueryPort` when V2-10 is merged; graceful empty when port returns empty.
- `PatientQrPort` bean issues and resolves tokens.
- Patient templates use shared layout fragments; no ownership violations.
