# T00 — Foundation & shared contracts (Wave 0, blocking)

You are the first agent on MediFlow AI. Read `docs/plan/PLAN.md` and `docs/plan/CONVENTIONS.md` fully. Research refs: PDF §7 (architecture), §8 (database design, ERD), §9 (data stores D1–D6), §12 (roles); DOCX §7.2 (stack), §9 (FHIR mapping), §11 (security).

## Goal
Create everything that all Wave-1 agents depend on so they can work in parallel without touching shared files.

## Owns
`backend/pom.xml`, `backend/mvnw*`, `backend/src/main/java/com/mediflow/{MediflowApplication.java,shared/**,config/**}`, `backend/src/main/resources/{application*.yml,db/migration/V1__*.sql..V99,templates/layout/**,static/**,messages/common_*.properties}`, `infra/docker-compose.yml`, `.env.example`, `.gitignore`, `README.md`, `ai-service/` **skeleton only** (folder + README stub).

## Deliverables
1. **Maven project** Java 21 / Spring Boot 3.x with deps: web, thymeleaf, thymeleaf-extras-springsecurity6, security, data-jpa, validation, flyway + postgresql, data-redis, session-data-redis, amqp, actuator, micrometer-prometheus, mail, `hapi-fhir-structures-r4`, AWS S3 SDK (MinIO), `dev.samstevens.totp`, zxing (QR), webjars bootstrap 5, lombok (optional), testcontainers (postgresql, rabbitmq), spring-security-test.
2. **docker-compose** (dev): postgres:16, redis:7, rabbitmq:3-management, minio, mailpit; `.env.example`.
3. **`V1__baseline.sql`** — full schema from PDF §8 + DOCX extras:
   `app_user, user_role, mfa_secret, consent, hospital, department, practitioner, practitioner_role, slot, patient, allergy, appointment, queue_entry (token_no, position, priority_level), triage, red_alert, encounter, vital_observation, vital_rule, condition_dx, clinical_note (versioned), lab_test (loinc_code, direct_allowed, price), lab_package, lab_package_item, lab_order (encounter_id nullable, order_type DOCTOR|DIRECT, status), sample, diagnostic_report (file_ref, verified_by, abnormal_flag, critical_flag), drug_generic (atc_code, rxnorm_code), drug_brand (dgda_reg_no, strength, form, generic_id), drug_interaction, drug_dose_limit, prescription (status DRAFT|SIGNED|AMENDED|ENTERED_IN_ERROR, signed_at, signed_by), prescription_item (brand_id null, generic_id not null, dose, route, frequency, duration, confidence, review_status, conflict_severity), ai_job, ai_flag_override, dispense, pharmacy_stock, reminder, notification, audit_event (append-only, prev_hash, hash), system_setting (versioned), setting_change_request (dual approval), break_glass_access`.
   Revoke UPDATE/DELETE on `audit_event` for the app role (trigger that raises on update/delete).
4. **JPA entities + Spring Data repositories** for every table in `com.mediflow.shared.domain` / `shared.repo`; shared enums (`Role, TriageLevel{CRITICAL,URGENT,NORMAL}, VitalClass{NORMAL,BORDERLINE,CRITICAL}, OrderType, PrescriptionStatus, ReviewStatus, ConflictSeverity{CRITICAL,WARNING,UNKNOWN}`...).
5. **Field encryption**: `@Convert` AES-256-GCM converter (key from env) used on `patient.national_id`, `patient.phone`, `patient.email`.
6. **Events** (records) listed in CONVENTIONS + **ports** (interfaces) with no-op/logging default beans (`@ConditionalOnMissingBean`): `AuditPort, NotificationPort, PatientAccessPort (isAssigned(doctorId, patientId)), ObjectStoragePort, ClockPort`.
7. **Security skeleton**: `SecurityFilterChain` with form login, deny-by-default, CSRF, secure headers (CSP, HSTS, frame-deny), session timeout 15 min, method security enabled, role hierarchy none; dev seed users per role (profile `dev` only).
8. **Layout**: `templates/layout/base.html` (Bootstrap 5, navbar rendered by role via `sec:authorize`, language switcher en/bn, flash messages, SSE helper JS in `static/js/sse.js`), placeholder nav links to each module root (`/patients`, `/directory`, `/appointments`, `/queue`, `/consultations`, `/prescriptions`, `/lab`, `/pharmacy`, `/admin`, `/audit/me`), and a trivial controller returning a "coming soon" page for each so links work.
9. i18n: `LocaleResolver` (cookie), `MessageSource` with basenames `messages/common, messages/identity, messages/patient, ...` for all modules (empty files created).
10. `GlobalExceptionHandler`, `BaseEntity` (id UUID, created_at, updated_at, version), `CurrentUser` helper.
11. Test: app context loads with Testcontainers; Flyway migrates; audit_event update is rejected.
12. README: how to run (`docker compose up -d`, `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`).

## Acceptance
- `./mvnw verify` green. App starts, login works for each seeded role, nav shows role-appropriate links.
- No business logic beyond the above — leave it to module owners.
