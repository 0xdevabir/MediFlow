# MediFlow AI v2 — Task index

Read first: `GAP-ANALYSIS.md`, then `PLAN.md`, then your task file under `docs/plan/tasks/`.

**Wave v2-W0 (merge first):** V2-01  
**Wave v2-W1 (parallel):** V2-02 … V2-12  
**Rules:** exclusive file ownership; cross-module only via V2-01 shared ports; no tests; no Docker; no secrets; never push `main` / never force-push.

| ID | Title | One-line summary |
|----|-------|------------------|
| [V2-01](../tasks/V2-01-shared-design-system.md) | Shared contracts & design system | Owns `shared/`, layout, CSS, `pom`/`yml`, and defines `LabOrderPort`, `DrugDictionaryPort`, `AdherenceQueryPort`, `PatientQrPort`, `MfaReauthPort` plus role-aware home shell. |
| [V2-02](../tasks/V2-02-identity-staff-access.md) | Identity staff UI & ER ACL | Staff CRUD screens; ER access uses `assigned_er_user_id`; implements `MfaReauthPort`. |
| [V2-03](../tasks/V2-03-patient-dashboard.md) | Patient dashboard & QR | Patient home/profile with adherence widget; implements `PatientQrPort`. |
| [V2-04](../tasks/V2-04-directory-polish.md) | Directory polish | Design-system search/admin directory; emergency numbers and booking handoff clarity. |
| [V2-05](../tasks/V2-05-appointment-queue-er.md) | Appointments, queue, ER | Always assign ER on Red Alert; polish queue/SSE/ER board UX for desk/nurse/ER. |
| [V2-06](../tasks/V2-06-clinical-consultation.md) | Clinical consultation | Drop local NoOp; consume shared `LabOrderPort`; follow-up events; vitals trend polish. |
| [V2-07](../tasks/V2-07-prescription-ux.md) | Prescription UX | Consume shared dictionary + MFA ports; encounter deep links; AI review UX/SSE polish. |
| [V2-08](../tasks/V2-08-lab-order-port.md) | Lab order port | Real `@Primary` `LabOrderPort` creating `lab_order` rows; consult/lab UX links. |
| [V2-09](../tasks/V2-09-pharmacy-dictionary.md) | Pharmacy dictionary | Implement shared `DrugDictionaryPort`; dispense/dictionary UI; QR at dispense. |
| [V2-10](../tasks/V2-10-notifications-adherence.md) | Notifications & adherence | Implement `AdherenceQueryPort`; ready adherence fragment; real schedule-change notifies. |
| [V2-11](../tasks/V2-11-admin-approver-audit.md) | Admin & clinical approver | Approver inbox UX; admin tiles linking staff/audit/analytics/DHIS2. |
| [V2-12](../tasks/V2-12-fhir-ai-polish.md) | FHIR & AI polish | Config-driven FHIR base URL; AI dictionary client aligned with pharmacy export. |

## Known gaps → tasks

| Gap | Task(s) |
|-----|---------|
| K1 / K5 Lab NoOp → real orders | V2-01, V2-08, V2-06 |
| K2 ER assignee unused by ACL | V2-02, V2-05 |
| K3 No staff management UI | V2-02, V2-11 (link), V2-01 (nav) |
| K4 DrugDictionaryPort not shared | V2-01, V2-09, V2-07 |
| K6 Patient adherence widget | V2-01, V2-10, V2-03 |
| T14 deferred functional rows | Mapped in `GAP-ANALYSIS.md` §7 |
| T14 test/Docker open issues | **Out of v2** |
