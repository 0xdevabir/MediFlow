# MediFlow AI v2 — Gap Analysis

**Branch / worktree:** `feat/V2-plan` (T00–T14 merged)  
**Sources (priority):** R&D PDF v1.3 > Technical DOCX v1.0 > poster photo > `docs/plan/PLAN.md` + reports/requests  
**Scope of this document:** frontend (Thymeleaf + Bootstrap 5) and backend completeness vs research. No tests/Docker work in v2.

Legend: **Done** · **Partial** · **Missing** · **Stub/NoOp** · **Broken/unused**

---

## 1. Cross-cutting platform gaps

| Area | Research expectation | Current state | Severity |
|------|---------------------|---------------|----------|
| Design system | Consistent Bootstrap 5 clinical UI, mobile-first, BN/EN | `app.css` ≈20 lines (teal vars only); templates use ad-hoc Bootstrap cards; no shared page/header/empty/status fragments | High (UX) |
| Role home | Role-based entry after login (F5 one login) | `HomeController` → generic `home.html` for every role | High |
| Nav / IA | Role-simple entry; ER, staff, audit, adherence reachable | Nav mostly present; staff CRUD unlinked; adherence fragment not on patient home; clinical approver lands on admin with limited cues | Medium |
| Shared ports | Modules talk only via `shared.port` / events | Only `AuditPort`, `ClockPort`, `NotificationPort`, `ObjectStoragePort`, `PatientAccessPort`. `LabOrderPort` lives under clinical; `DrugDictionaryPort` under pharmacy; Rx uses parallel `RxDrugDictionary` | High |
| PlaceholderController | Module roots owned | Only `/consultations` alias + `/modules/{module}` remain | Low |
| SSE live updates | Queue token, AI job, ER board | `sse.js` + queue/Rx clients exist; inconsistent loading/reconnect UX; no shared SSE status component | Medium |
| Bangla / English | All screens + SMS | Message bundles exist per module; not every new string dual-keyed; some placeholders still English-only in forms | Medium |
| Accessibility | Usable clinical UI | Bootstrap defaults; missing skip-link, consistent headings, focus states, aria on live regions | Medium |
| Follow-up dates | Emit `FollowUpScheduled` → reminders | Event + listener exist; clinical/Rx have no follow-up date field / publisher | High |
| FHIR base URL | Configurable | `mediflow.fhir.base-url` in yml; `FhirSystems.FHIR_BASE` still hard-coded | Low |
| Logging stubs | Real adapters | Audit/Notification/Storage replaced; `LoggingPortStubs` remains as fallback only | OK |
| Push gateway | Real FCM later | `LoggingPushGateway` stub (acceptable for phase; document) | Low |
| DHIS2 org unit | Facility mapping | Placeholder string `"MediFlow"` | Low |
| Drug safety rules | Licensed interactions / dose limits | Seed rows marked `PLACEHOLDER_SEED` | Medium (clinical) |

---

## 2. Known gaps called out for v2 (must close)

| # | Gap | Evidence | Closes in |
|---|-----|----------|-----------|
| K1 | Lab orders from consultation are a **NoOp** port | `NoOpLabOrderPort` + `@ConditionalOnMissingBean`; publishes event only; returns `UUID(0,0)`; no `lab_order` row | V2-01 (port move) + V2-08 (bean) + V2-06 (consume) |
| K2 | ER red-alert **assignee not used** by access policy | Column `assigned_er_user_id` set in `RedAlertService`; `IdentityPatientAccessPort.isErAssigned` / `AccessPolicy` ER_ASSIGNED only check `activeRedAlert(patientId)` (any ER) | V2-02 |
| K3 | **No staff management UI** | `StaffUserService` complete; no controller/templates; T14 deferred “Staff CRUD page” / “Admin menu links / staff UI” | V2-02 (+ nav link V2-01) |
| K4 | **DrugDictionaryPort not shared** | Port in `pharmacy.dictionary`; Rx has `RxDrugDictionary`; T14 deferred promote to `shared.port` | V2-01 (interface) + V2-09 (impl) + V2-07 (adopt) |
| K5 | **Real LabOrderPort** | Same as K1 | V2-08 |
| K6 | **Patient dashboard adherence widget** | `notification/fragments :: adherence` exists; commented include hint; patient profile/home never includes it; reminders page shows % only | V2-01 (AdherenceQueryPort) + V2-10 (impl) + V2-03 (include) |
| K7 | Every **T14 deferred** row (see §7) | `docs/plan/reports/T14.md` | Mapped in §7 → tasks |

---

## 3. Per-role matrix (UI + backend)

### 3.1 Patient

| Capability (R&D §2 / F1–F7) | Backend | UI | Gap |
|----------------------------|---------|----|-----|
| Register + consent | Done | `patient/register.html` | Polish + design system |
| Profile / demographics (own contact) | Done | `profile.html` | Name/DOB change request flow Partial |
| Hospital & doctor directory search | Done | `directory/search.html` | Anonymous nav deferred; body-part search Partial |
| Book appointment + see confirmation | Done | `appointment/*` | Role-home CTA Missing |
| Direct lab test / packages | Done | `lab/direct.html`, directory tests | Cost-before-book (F Mayo idea) Missing |
| Queue token + patients ahead (SSE) | Done | `queue/patient.html` | Mobile token card polish |
| View e-prescriptions | Done | `prescription/list` (patient) | Patient-friendly copy Partial |
| View lab reports + abnormal advice | Done | `lab/reports*` | “See a doctor” booking deep-link Partial |
| Reminders + adherence | Done service | reminders page; **dashboard widget Missing** | K6 |
| One login for booking/reports/Rx | Done auth | Home not role-tailored | Role dashboard Missing |
| Bangla/English | Partial | EN/BN toggles | Coverage audit |
| Audit “who accessed me” | Done | `audit/me.html` | OK |

### 3.2 Front desk

| Capability | Backend | UI | Gap |
|------------|---------|----|-----|
| Walk-in registration / patient search | Done | `patient/search`, register | Dashboard Missing |
| Book appt / direct lab for patient | Done | appointment + lab direct | OK functional |
| Check-in + token | Done | `appointment/checkin`, queue | OK |
| Suspected emergency flag | Done | triage / emergency on booking | Surface on check-in Partial |
| Queue board | Done | `queue/board.html` | Design polish |

### 3.3 Nurse

| Capability | Backend | UI | Gap |
|------------|---------|----|-----|
| Triage Critical/Urgent/Normal | Done | appointment/queue flows | Dedicated nurse home Missing |
| Vitals entry + classification | Done | `clinical/vitals-entry.html` | Chart.js trend deferred |
| Auto Red Alert on critical vitals | Done (event → T04) | — | OK |
| Urgent queue reorder | Done | queue doctor/board | Nurse-specific IA Partial |
| Staff call list | Done | `notification/call-list.html` | OK |

### 3.4 Doctor

| Capability | Backend | UI | Gap |
|------------|---------|----|-----|
| Assigned-patient consult list | Done | `clinical/consultation-list.html` | Role home Missing |
| History, allergies, vitals trend | Done | consultation + vitals-trend | Chart.js optional deferred |
| Notes + ICD-10 diagnosis | Done | `consultation.html` | OK |
| Order lab from consult | **Stub** | Form posts `testCode` → NoOp | **K1** — no real order |
| Link to lab orders for encounter | Partial | Lab has doctor-order page; consult deep-link deferred | Cross-module UI |
| AI e-Rx + SSE + 85% gate + safety | Done | `prescription/*` + `ai-job.js` | Deep link `?encounterId=` unverified; dictionary dual API |
| E-sign (licence + MFA step-up) | Done | review flow | OK |
| Addendum / revoke | Done | copy/revoke paths | OK |
| Follow-up date → reminders | **Missing** | No field | Emit `FollowUpScheduled` |
| Pharmacy substitution respond | Done | pharmacy home pending list | OK |

### 3.5 ER

| Capability | Backend | UI | Gap |
|------------|---------|----|-----|
| Receive Red Alert + summary | Done | `er/board.html`, `detail.html` | Design polish |
| Acknowledge + handover status | Done | ER detail | OK |
| Access limited to **assigned** Red-Alert patient | **Broken vs R&D** | — | **K2** — any ER sees any active alert patient |
| Nav `/er` | Done | base nav | OK |

### 3.6 Lab tech

| Capability | Backend | UI | Gap |
|------------|---------|----|-----|
| Doctor-ordered + direct queues | Done | `lab/queue`, tech-order | OK |
| Sample tracking | Done | workflow statuses | OK |
| Upload + verify reports | Done | report-detail | OK |
| Abnormal → patient advice; critical → call + duty doctor | Done (notifications) | flags in UI | Call-list integration Partial |
| AccessPolicy vs local LabAccess | Dual | — | T14 deferred; converge on `@access` |

### 3.7 Pharmacist

| Capability | Backend | UI | Gap |
|------------|---------|----|-----|
| Signed Rx queue + dispense | Done | `pharmacy/dispense.html` | OK |
| Stock / batches / substitutions | Done | stock, batches, substitutions | OK |
| DGDA dictionary admin + import | Done | dictionary templates | Shared port Missing (**K4**) |
| Patient QR identity | Partial | PatientIdentityPort defaults | PatientQrPort deferred |
| Second-check safety | Done against local dictionary | — | Should use shared DrugDictionaryPort |

### 3.8 Admin

| Capability | Backend | UI | Gap |
|------------|---------|----|-----|
| Hospital / dept / doctor / test / package directory | Done | `directory/admin/*` | OK |
| Direct-test list + packages | Done | test-form, package-form | OK |
| Thresholds raise-only + dual approval | Done | `admin/settings.html` | Approver UX Partial |
| Analytics KPIs | Done | `admin/analytics.html` | OK |
| DHIS2 aggregate export | Done | `admin/dhis2.html` | Org-unit placeholder |
| Audit search / chain / anomalies | Done | `audit/*` | Nav polish deferred |
| **Staff users CRUD** | Service Done | **UI Missing** | **K3** |
| Break-glass review | Done | identity templates | Link from admin dashboard Partial |

### 3.9 Clinical approver

| Capability | Backend | UI | Gap |
|------------|---------|----|-----|
| Dual approval on clinical thresholds | Done (`SettingGovernanceService`) | Shares admin settings | Dedicated approver inbox / cues Missing |
| MFA mandatory | Done | MFA flows | OK |
| No patient PHI (aggregate only) | Access matrix | Admin dashboard shows chain for both roles | Harden model attributes for non-admin |

---

## 4. Module-by-module backend / UI inventory

| Module | Backend maturity | UI maturity | Top gaps |
|--------|------------------|-------------|---------|
| `shared` / `config` | Solid foundation | Minimal layout/css | Design system; shared ports; role home; CSRF cookie note |
| `identity` | Strong | MFA/password/break-glass/sharing | Staff UI; ER assignee in facts |
| `patient` | Strong | Functional | Dashboard; adherence include; `/patients` role redirect polish |
| `directory` | Strong | Search + admin | Anonymous nav; JPA column mapping optional |
| `appointment` (+ queue, ER, red alert) | Strong | Present | Assignee enforcement elsewhere; SlotCatalog vs DirectoryQuery optional |
| `clinical` | Strong except lab port | Consult + vitals | Real orders; follow-up date; Chart.js; `@access` bridge |
| `prescription` | Strong | Review + AI | Shared dictionary; encounter deep link verify; follow-up |
| `lab` | Strong | Hub + flows | Register `LabOrderPort` `@Primary`; consult link; AccessPolicy |
| `pharmacy` | Strong | Hub + flows | Promote dictionary port; shared events; QR port |
| `notification` | Strong | Inbox/reminders/bell | Dashboard adherence; schedule blast beyond log |
| `admin` / `audit` | Strong | Dashboards | Staff link; approver UX |
| `fhir` | Facade Done | N/A (JSON) | Wire `FhirSystems` to config |
| `ai-service` | Pipeline Done | N/A | Align export contract if shared dictionary changes |

---

## 5. Frontend quality gaps (all roles)

1. **No design system** — tokens, type scale (Latin + Bengali), spacing, status colors (critical/urgent/normal/amber AI), buttons, forms, tables, empty states, toast/flash pattern beyond basic alerts.
2. **No role dashboards** — post-login `/` is a three-line welcome; R&D wants role-simple entry.
3. **Inconsistent page chrome** — some pages `card card-body shadow-sm`, others bare forms; no shared `page-header` / breadcrumbs.
4. **Mobile** — navbar collapses, but queue token, ER board, Rx review, vitals forms need thumb-first layouts and sticky primary actions.
5. **Live regions** — SSE updates should announce via `aria-live` (queue position, AI job status, ER new alert).
6. **Clinical density** — doctor consult packs notes, allergies, labs, Rx links without clear hierarchy.
7. **Coming-soon / modules placeholder** — still reachable via `/modules/{module}`; retire or redirect.

---

## 6. Backend completion gaps (functional correctness)

1. **NoOp lab path** — doctor believes an order was placed; lab never receives a row (safety/continuity bug).
2. **ER access over-broad** — violates R&D §12 footnote (“limited to the Red-Alert patient they are assigned”).
3. **Dictionary duplication** — risk of Rx safety and pharmacy dispense disagreeing on brand→generic.
4. **Follow-up silent** — adherence/reminders under-fed without `FollowUpScheduled` publishers.
5. **Staff ops** — cannot provision users without DB/seed (blocks real pilots).
6. **AccessPolicy adoption** — lab/clinical still mix local guards; matrix drift risk.
7. **Schedule-change notifications** — listener log-only; patients with booked slots not messaged.

---

## 7. T14 deferred / open → v2 disposition

### Deferred request rows

| Source | Item | v2 disposition |
|--------|------|----------------|
| T01 | Wire `IdentityAuthenticationProvider` explicitly | V2-02 (if still sole provider, document or wire) |
| T01 | CSRF SameSite=Strict on XSRF cookie | V2-01 (`SecurityConfig` / cookie customizer) |
| T01 | Red-alert assignee used by access | **V2-02** (K2) |
| T01 | Admin menu links / staff UI | **V2-02** UI + **V2-01** nav (K3) |
| T01 | Clinical `@access` guards everywhere | V2-02 policy helpers + V2-06/08 adopt |
| T02 | ConsentService `@Primary` adapter | V2-03 if still dual; else document |
| T02 | Document `mediflow.patient.qr-token-ttl` | V2-01 yml comment / property doc |
| T02/T10 | Adherence widget on patient dashboard | **V2-03 + V2-10** (K6) |
| T03 | Nav anonymous directory | V2-01 nav `permitAll` link styling |
| T03 | Update `LoginAndNavigationIT` | **Out of v2** (no tests rule) — note only |
| T03 | Map hospital/dept JDBC columns on JPA | V2-04 optional if validate breaks |
| T04 | Prefer DirectoryQueryService port | V2-05 soft; SlotCatalog OK if documented |
| T05 | Real `LabOrderPort` bean | **V2-08** (K1/K5) |
| T05 | ClinicalAccess → AccessPolicy bridge | V2-06 |
| T05 | `/prescriptions?encounterId=` deep link | V2-07 verify + fix |
| T05 | Chart.js webjar / allergy columns | V2-06 (+ V2-01 webjar if needed) |
| T06 | Resilience4j dep | V2-01 pom optional / V2-07 adopt or keep breaker |
| T06 | Shared MFA port | V2-01 port stub + V2-02 impl **or** document existing OK |
| T06 | Seed drug / Primary dictionary | V2-09 |
| T07 | Extra lab nav role links | V2-01 nav polish |
| T07 | Consultation → lab orders link | V2-06 UI + V2-08 routes |
| T07 | AccessPolicy lab order | V2-08 + V2-02 |
| T08 | Promote DrugDictionaryPort to shared | **V2-01 + V2-09 + V2-07** (K4) |
| T08 | Shared pharmacy domain events | V2-01 events + V2-09 publish |
| T08 | PatientQrPort | V2-01 port + V2-03/V2-09 |
| T10 | Adherence on patient dashboard | same as T02 |
| T11 | Admin → `/admin/audit` nav | V2-01 / V2-11 |
| T11 | Staff CRUD page | **V2-02** |
| T12 | `FhirSystems` reads `mediflow.fhir.base-url` | V2-12 |
| T14 deviations | `PractitionerScheduleChanged` real recipients | V2-10 |
| T14 deviations | Emit `FollowUpScheduled` | V2-06 + V2-07 |
| T14 open 1–8 | Docker / verify / Playwright / k6 / ZAP / OWASP / Argon2 check | **Explicitly out of v2** (user rule: no tests/Docker) |

---

## 8. Out of scope (research + user rules)

- Inpatient, billing/payment, DICOM, video consult, medicine delivery, autonomous diagnosis, Android app  
- Running or authoring automated tests, Docker Compose validation, k6, ZAP  
- Replacing Bootstrap 5 / Thymeleaf stack  

---

## 9. Verdict

v1 (T00–T14) delivered a **working modular monolith** covering nearly every R&D module surface, but it is **not production-complete**:

1. One clinical path is fake (consult → lab NoOp).  
2. One access rule is wrong vs R&D (ER assignee).  
3. Pilot ops blocked (no staff UI).  
4. Safety dictionary not a single shared contract.  
5. Patient adherence KPI UI incomplete.  
6. Frontend is functional Bootstrap, not a coherent clinical product UI with role dashboards.

v2 closes those correctness gaps and brings UI/UX to production quality on the existing Thymeleaf + Bootstrap 5 stack.
