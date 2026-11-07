# T11 — Audit & compliance (M9) + Admin configuration & analytics (M10)

Read PLAN + CONVENTIONS. Research: DOCX §11.1 "Audit", Table 17 footnotes ⁴ ⁵, §10.7, Table 20 S7, Table 23; PDF §10 "National alignment" (DHIS2), §14 KPI table.

## Owns
`com.mediflow.audit/**`, `com.mediflow.admin/**`, `templates/audit/**`, `templates/admin/**`, `messages/{audit,admin}_*`, V550–V599.

## Build — Audit
1. Implement `AuditPort.record(actor, action, resourceType, resourceId, patientId, outcome, purpose)`; writes `audit_event` with **hash chain** (`hash = SHA-256(prev_hash || canonical row)`), async-safe ordering (single writer / advisory lock).
2. Verifier job + admin page "chain integrity OK / broken at #n".
3. Patient page `/audit/me`: who accessed my record and when (role + name, purpose).
4. Admin audit search (filters: user, patient, action, date); break-glass review list with "reviewed" sign-off; alert on anomalies (e.g., > N patient reads/hour by one user).
5. Retention policy config + export (CSV) for compliance.

## Build — Admin (M10)
6. Admin dashboard linking to T03 directory CRUD and T01 user management.
7. **Settings with governance**: `system_setting` versioned; changes to clinical rules (vital_rule, interaction set) and the **AI confidence threshold** go through `setting_change_request` → requires ADMIN + CLINICAL_APPROVER (two different users). Threshold: **raise-only** (reject lower values at service level), audited.
8. Analytics (aggregated, no clinical content for admin): appointment-to-consultation time, arrival-to-ER time, arrival-to-triage (urgent), order-to-report time (doctor vs direct), share of direct tests followed by doctor visit, AI fields accepted unchanged / corrected, prescriptions needing clarification, reminder acknowledgement rate.
9. **DHIS2 export**: anonymised aggregated indicators (visits, lab turnaround, prescriptions issued, emergency cases) as DHIS2 `dataValueSets` JSON download (live push disabled until DGHS approval).

## Acceptance
- Tests: tamper detection; DB rejects update/delete; threshold lowering rejected; single-person approval rejected; admin cannot open clinical content.
