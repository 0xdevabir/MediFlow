# MediFlow AI — Implementation Plan

Source of truth (in priority order — newer wins on conflict):
1. `research/MediFlow_AI_RD_Documentation_Final.pdf` — **v1.3, Oct 2026** (adds hospital directory, direct lab tests, Red Alert → ER, DGDA brand mapping, SSE, per-item 85 % gate, polymorphic vitals, front desk / ER roles)
2. `research/MediFlow_AI_Technical_Documentation.docx` — v1.0 (full security, FHIR, risk, evaluation detail)
3. `research/photo_2026-10-03 14.13.27.jpeg` — poster (intro / outcomes / 7-step methodology)

## 1. What we are building

A web-first, security-first platform that links **Patient → Front desk → Nurse → Doctor → Lab → Pharmacy → Follow-up**, with:

| # | Capability | Module |
|---|---|---|
| F2 | Hospital / branch / department / doctor directory with hours, emergency phone, free slots | M2 + M10 |
| — | Registration, consent, one login for everything (F5) | M1 + M7 |
| — | Appointment booking, check-in, live queue token (F4) | M2 |
| N5 | Triage: Critical → **Red Alert → ER**; Urgent → priority OPD; Normal → FIFO by slot; critical vitals auto-escalate | M2 |
| N3 | Vitals: polymorphic `VitalSign` (`NumericVital`, `BloodPressureVital`), single `VitalRule` table, MAP + shock index, trend history | M3 |
| — | Consultation, ICD-10 diagnosis, versioned notes | M3 |
| N1/N4 | AI e-prescription: photo/typed → OCR → NER → DGDA brand→generic→ATC → per-item confidence; ≥ 0.85 pre-fill, < 0.85 amber, critical conflict hard stop; async job via RabbitMQ, result via **SSE**, poll fallback, **10 s → manual** | M4 + AI service |
| N2 | Contradiction engine (allergy, DDI, dose range, duplicate generic, condition/pregnancy/vitals, unknown item) | M4 |
| — | Doctor e-sign (licence + MFA re-prompt), addendum-only after signing, revoke = entered-in-error | M4 |
| N6/F1/F3 | Lab: doctor-ordered + **direct tests** from approved list + packages; sample tracking; verify; abnormal → "see a doctor"; critical → phone + duty doctor alert | M5 |
| — | Pharmacy: validate, stock, dispense, QR identity token | M6 |
| — | Reminders (medicine, visit, report) SMS/email/push, **Bangla + English** (F6) | M8 |
| — | RBAC matrix (7 roles + ER), assigned-patient rule, break-glass, MFA | M7 |
| — | Append-only, hash-chained audit; patient sees who accessed record | M9 |
| — | Admin: directory, direct-test list, thresholds (raise-only, dual approval), analytics, DHIS2 aggregate export | M10 |
| — | HL7 FHIR R4 read API | FHIR facade |

**Out of scope:** inpatient beds, billing/insurance/online payment, DICOM, video consult, medicine delivery, autonomous diagnosis, Android app.

## 2. Tech stack (fixed — do not substitute)

| Layer | Choice |
|---|---|
| Backend + UI | Java 21, Spring Boot 3.x, Maven, Spring Data JPA, **Thymeleaf + Bootstrap 5**, minimal vanilla JS (SSE client only) |
| Security | Spring Security 6, session cookies (HttpOnly, SameSite=Strict), CSRF on, BCrypt/Argon2, TOTP MFA, method security |
| DB | PostgreSQL 16 + **Flyway**; JSONB for FHIR payloads; pgcrypto / JPA converter for field-level encryption |
| Cache | Redis (sessions via Spring Session, drug dictionary, rule sets) |
| Queue | RabbitMQ (AI jobs, notifications) |
| Objects | MinIO (S3 API) — prescription photos, report PDFs, encrypted |
| AI service | Python 3.11, FastAPI, OpenCV, PaddleOCR (Tesseract fallback), spaCy/rules NER, RapidFuzz, pika/aio-pika |
| FHIR | HAPI FHIR structures R4 (library only, no HAPI server) |
| Tests | JUnit 5, Testcontainers, Spring Security Test, pytest, OWASP ZAP baseline, k6 |
| DevOps | Docker Compose, Nginx (TLS), GitHub Actions, Prometheus + Grafana (Actuator / prometheus_client) |

## 3. Repository layout

```
backend/                       Spring Boot app (modular monolith)
  src/main/java/com/mediflow/
    shared/                    T00 — base entity, events, ports (interfaces), errors, encryption
    identity/      (M7)        T01
    patient/       (M1)        T02
    directory/     (M2/M10 D6) T03
    appointment/   (M2)        T04   incl. queue, triage, redalert
    clinical/      (M3)        T05   consultation, vitals
    prescription/  (M4)        T06   drafts, AI gateway, SSE, safety engine, e-sign
    lab/           (M5)        T07
    pharmacy/      (M6)        T08   incl. drug dictionary
    notification/  (M8)        T10
    audit/         (M9)        T11
    admin/         (M10)       T11
    fhir/                      T12
  src/main/resources/
    db/migration/              V1__baseline.sql (T00) + module ranges (see CONVENTIONS)
    templates/<module>/        each module owns its folder
    messages/<module>_{en,bn}.properties
ai-service/                    T09 FastAPI worker
infra/                         T00 compose; T13 nginx, monitoring, CI
docs/plan/                     this plan + task prompts
```

## 4. Execution waves (who runs when)

```
Wave 0  (1 agent, blocking)      T00 Foundation & shared contracts
           │
Wave 1  (parallel, 10 agents)    T01 Identity/RBAC   T02 Patient    T03 Directory
           │                     T04 Appointments/Queue/RedAlert    T05 Consultation/Vitals
           │                     T06 Prescription+Safety engine     T07 Lab
           │                     T08 Pharmacy+DGDA                  T09 AI service (Python)
           │                     T10 Notifications                  T11 Audit+Admin
           │
Wave 2  (parallel, 2 agents)     T12 FHIR R4 facade    T13 DevOps/CI/Monitoring
           │
Wave 3  (1 agent)                T14 Integration, E2E, RBAC-matrix tests, ZAP, k6, safety drills
```

Why this order: T00 creates **the whole DB schema, all JPA entities/repositories, all cross-module events and port interfaces, the security role model and the base layout/nav**. After that, every Wave-1 agent touches only its own package, templates folder, messages file and migration range → no merge conflicts and no agent waits for another. Cross-module calls go through the ports in `shared/` (implemented by the owner, stubbed by T00).

## 5. Agent workflow (every task)

1. Branch/worktree: `feat/<task-id>` off `main` after Wave 0 is merged.
2. Read `docs/plan/PLAN.md`, `docs/plan/CONVENTIONS.md`, own task file, and the cited research sections.
3. Stay inside "Owns" paths. Need a change elsewhere? Write it in `docs/plan/requests/<task-id>.md` instead.
4. Done = acceptance criteria pass + `./mvnw -q verify` (or `pytest`) green + short `docs/plan/reports/<task-id>.md` (what was built, deviations, open issues).

## 6. Safety invariants (every agent must preserve)

- AI never signs, never dispenses, never writes to the record directly — it only produces a draft.
- Per item: confidence = min(drug, dose, frequency). ≥ 0.85 and no conflict → pre-filled; < 0.85 → amber, must be resolved; critical conflict → hard stop at any confidence; unknown brand → treated as < 0.85 and blank.
- Whole draft → manual only if image unreadable, AI > 10 s, or service down.
- **Unknown is never safe.** Threshold can only be raised, only with dual approval, versioned + audited.
- Critical vitals always raise Red Alert. Nurse override always logged.
- Direct-test report visible to patient only after lab verification.
- Deny by default; doctors see assigned patients only; every read/write audited.
- No patient data to third-party AI services.

## 7. Task index

| ID | Title | Wave | File |
|---|---|---|---|
| T00 | Foundation & shared contracts | 0 | tasks/T00-foundation.md |
| T01 | Identity, RBAC, MFA, consent, break-glass (M7) | 1 | tasks/T01-identity-rbac.md |
| T02 | Patient registration & profile (M1) | 1 | tasks/T02-patient.md |
| T03 | Hospital & doctor directory, search (D6) | 1 | tasks/T03-directory.md |
| T04 | Appointments, check-in, queue token, triage, Red Alert (M2) | 1 | tasks/T04-appointment-queue.md |
| T05 | Consultation & vitals (M3) | 1 | tasks/T05-consultation-vitals.md |
| T06 | Prescription workflow, AI gateway, SSE, safety engine, e-sign (M4) | 1 | tasks/T06-prescription.md |
| T07 | Lab coordination incl. direct tests (M5) | 1 | tasks/T07-lab.md |
| T08 | Pharmacy & DGDA drug dictionary (M6) | 1 | tasks/T08-pharmacy.md |
| T09 | AI service — OCR/NER/normalisation/scoring (Python) | 1 | tasks/T09-ai-service.md |
| T10 | Notifications & reminders, Bangla/English (M8) | 1 | tasks/T10-notifications.md |
| T11 | Audit (M9) + Admin config & analytics (M10) | 1 | tasks/T11-audit-admin.md |
| T12 | FHIR R4 facade | 2 | tasks/T12-fhir.md |
| T13 | DevOps, CI/CD, TLS, monitoring | 2 | tasks/T13-devops.md |
| T14 | Integration, E2E & security validation | 3 | tasks/T14-integration-tests.md |
