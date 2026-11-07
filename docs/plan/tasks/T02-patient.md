# T02 — Patient registration & profile (M1)

Read PLAN + CONVENTIONS. Research: PDF §6 feature table "Registration", §8 `patient`, `allergy`; DOCX Table 5, Table 17 footnotes ¹ ², Table 19 R5.

## Owns
`com.mediflow.patient/**`, `templates/patient/**`, `messages/patient_*`, V150–V199.

## Build
1. Self sign-up (patient): demographics (name, DOB, sex, phone, email, national ID optional), consent checkbox (via T01 `ConsentService` — if not merged yet, code against the interface), phone/email verification by OTP (send via `NotificationPort`).
2. **Front-desk walk-in registration** (ROLE_FRONT_DESK) with the same form; duplicate detection on (national_id) or (name + DOB + phone).
3. MRN generation (`MF-YYYY-NNNNNN`), printable patient card with **QR token** (signed, short-lived) used for identity at pharmacy/lab.
4. Profile page: patient can update **contact details only**; name/DOB change creates a verification request handled by front desk.
5. Self-reported allergies & history: stored with `verified=false` + "unverified" badge; doctor confirm action exposed as service method for T05.
6. Two-identifier match helper `PatientIdentityService.verify(mrnOrNationalId, dob)` (risk R5).
7. Document upload (ID scan) to MinIO via `ObjectStoragePort`.
8. Publish `PatientRegistered`. Audit every read/write.
9. Bangla + English strings.

## Acceptance
- Tests: registration, duplicate detection, contact-only update rule, unverified allergy flag, identity verify, RBAC (patient sees only self).
