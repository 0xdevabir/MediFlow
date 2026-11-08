# V2-11 — Admin, clinical approver UX, audit polish

**Wave:** v2-W1  
**Branch:** `feat/V2-11-admin-approver-audit`  
**Depends on (by name):** V2-01 layout/nav; staff UI lives under identity (`/identity/staff*` from V2-02) — link only

## Owns

- `backend/src/main/java/com/mediflow/admin/**`
- `backend/src/main/java/com/mediflow/audit/**`
- `backend/src/main/resources/templates/admin/**`
- `backend/src/main/resources/templates/audit/**`
- `backend/src/main/resources/messages/admin_en.properties`
- `backend/src/main/resources/messages/admin_bn.properties`
- `backend/src/main/resources/messages/audit_en.properties`
- `backend/src/main/resources/messages/audit_bn.properties`
- Flyway `V750__*` … `V759__*`

## Build

1. Admin dashboard: tiles for directory, **staff** (link to V2-02 UI), settings, analytics, DHIS2, audit search/chain/anomalies, break-glass review URL.
2. **Clinical approver UX:** pending dual-approval queue primary on `/admin` when role is CLINICAL_APPROVER; hide PHI/anomaly patient details from non-ADMIN; clear approve/reject actions on settings governance.
3. Settings / analytics / DHIS2 pages → design system; note org-unit placeholder honestly in UI.
4. Audit patient `me` + admin search: clearer tables, filters, empty states.
5. Optional home fragments for ADMIN / CLINICAL_APPROVER.
6. EN+BN; no staff CRUD duplication (identity owns that).

## Acceptance

- CLINICAL_APPROVER can find and act on pending threshold approvals without admin-only PHI widgets.
- ADMIN dashboard links to staff management and audit.
- Templates use shared layout; ownership respected.
