# T07 — Lab coordination incl. direct tests (M5)

Read PLAN + CONVENTIONS. Research: PDF §5 "How the direct lab test works", Figures 6A and 6C, §9.2 flows 9, 18–22, risk "Direct test misused"; DOCX Table 5 "Laboratory coordination".

## Owns
`com.mediflow.lab/**`, `templates/lab/**`, `messages/lab_*`, V400–V449.

## Build
1. **Doctor-ordered**: service `LabOrderService.orderForEncounter(encounterId, testIds)` (order_type DOCTOR); publish `LabOrderCreated`.
2. **Direct test** (patient, or front desk for walk-in): pick test(s) or package from `direct_allowed` list only (server-side validation), show price, choose slot/date; order_type DIRECT, encounter null, requester = patient.
3. **Lab tech queue**: orders list by status (ORDERED → SAMPLE_COLLECTED → IN_PROGRESS → REPORTED → VERIFIED); sample barcode/QR, identity check with QR token.
4. **Report upload** (PDF to MinIO + structured result values with reference ranges, LOINC/UCUM) → abnormal/critical flags computed from ranges; **verify** step by a different lab tech/verifier.
5. **Release rules**: patient sees report **only after verification**. Abnormal direct test → "please see a doctor" message + booking link. Critical value → task for verifier "phone patient" (must be marked done) + duty doctor alert. Doctor-ordered → doctor notified.
6. Publish `LabReportVerified(abnormal, critical, orderType)`.
7. Patient "My lab reports" page.

## Acceptance
- Tests: non-direct test cannot be booked directly; unverified report invisible to patient; abnormal → advice notification; critical → phone task + doctor alert; lab tech only sees orders they handle (RBAC).
