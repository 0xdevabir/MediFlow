# V2-02 — Identity: staff UI, ER assignee ACL, access hardening

**Wave:** v2-W1  
**Branch:** `feat/V2-02-identity-staff-access`  
**Depends on (by name):** V2-01 `MfaReauthPort`; nav link to staff pages added by V2-01 (or relative URLs `/identity/staff`)

## Owns

- `backend/src/main/java/com/mediflow/identity/**`
- `backend/src/main/resources/templates/identity/**`
- `backend/src/main/resources/messages/identity_en.properties`
- `backend/src/main/resources/messages/identity_bn.properties`
- Flyway `V660__*` … `V669__*`

## Build

1. **Staff management UI** on top of existing `StaffUserService`:
   - List staff (`listStaff`), create user, assign roles, enable/disable, unlock, reset MFA, link/create practitioner.
   - Templates e.g. `identity/staff-list.html`, `identity/staff-form.html`; controller under `identity.staff` or `identity.web`.
   - `@PreAuthorize("hasRole('ADMIN')")`; audit every mutation via `AuditPort`.
2. **ER assignee enforcement (K2):**
   - Update `AccessFacts` / `IdentityPatientAccessPort.isErAssigned` so ER access requires an **active** red alert for that patient whose `assigned_er_user_id` equals the ER user (or break-glass). If assignee is null, fall back to hospital-scoped duty policy **or** treat as unassigned (document choice; prefer: only assignee + break-glass, and ensure `RedAlertService` always sets assignee — do not edit appointment package; if null assignees exist, allow hospital ER pool only when `assigned_er_user_id IS NULL` **and** document — R&D wants assigned).
   - Align `AccessPolicy` `ER_ASSIGNED` branch with the same rule.
3. Implement `MfaReauthPort` bean delegating to existing `MfaReauthService` / step-up helpers.
4. Wire or document `IdentityAuthenticationProvider` as explicit security collaborator if still implicit-only.
5. Expand `@access` helpers used by other modules (SpEL methods already on `AccessPolicy`); add any missing resource/action cells from R&D §12 without editing other modules’ annotations (those modules adopt in their tasks).
6. Clinical approver: ensure break-glass review / sharing pages remain ADMIN; no PHI lists for CLINICAL_APPROVER.
7. Adopt layout fragments from V2-01 (`page-shell`, badges); EN+BN strings for all new UI.
8. Home fragment optional: `identity/fragments :: admin-staff-tile` for ADMIN dashboard include (V2-01 home may `th:replace` if present).

## Acceptance

- Admin can create a pharmacist user, assign `ROLE_PHARMACIST`, disable, and unlock from the UI.
- An ER user who is **not** `assigned_er_user_id` on a patient’s active alert is denied patient clinical reads that use `PatientAccessPort` / `@access` ER paths; assignee is allowed.
- `MfaReauthPort` is a Spring bean.
- Staff pages match design system; no edits outside Owns.
