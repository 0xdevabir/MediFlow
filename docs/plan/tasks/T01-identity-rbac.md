# T01 — Identity, RBAC, MFA, consent, break-glass (M7)

Read `docs/plan/PLAN.md`, `docs/plan/CONVENTIONS.md`. Research: PDF §12 (access matrix + footnotes), DOCX §11.1–11.3 (controls, RBAC matrix, consultation rights), DOCX Table 19 R4/R5.

## Owns
`com.mediflow.identity/**`, `templates/identity/**`, `messages/identity_*`, Flyway V100–V149.

## Build
1. Login/logout pages, account lock-out (5 fails → 15 min), password policy, Argon2 encoder, password reset by email token.
2. **TOTP MFA** enrolment (QR) + verification step; mandatory for DOCTOR, PHARMACIST, ADMIN, CLINICAL_APPROVER. `MfaReauthService.requireFreshMfa()` used by T06 for e-sign (step-up within last 5 min).
3. Staff user management service (used by T11 admin UI): create user, assign roles, link to `practitioner`.
4. Implement `PatientAccessPort`: doctor is assigned if patient has a checked-in appointment/encounter with that doctor today, an active referral, or active break-glass; ER role assigned if a Red Alert for that patient is assigned to them. Patients → own record only.
5. A reusable `@PreAuthorize` bean `@access.canReadPatient(#patientId)` / `canWritePatient`, `canReadLabOrder`, `canReadPrescription` implementing the **minimum-necessary** footnotes (lab tech/pharmacist only data linked to an order/Rx they handle).
6. **Break-glass**: form with mandatory reason → time-limited (60 min) access row, instant notification to admin via `NotificationPort`, appears in admin review list.
7. **Consent**: capture at registration (T02 calls `ConsentService`), time-limited referral sharing ("share with another doctor").
8. Patient "who accessed my record" page is T11's — expose nothing here.

## Acceptance
- Parameterised test covering **every cell** of the PDF §12 access matrix for the beans in (5).
- MFA required flows tested; lock-out tested; break-glass expiry tested.
