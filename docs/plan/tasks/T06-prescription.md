# T06 — Prescription workflow, AI gateway, SSE, contradiction engine, e-sign (M4)

**Most safety-critical task.** Read PLAN (§6 Safety invariants) + CONVENTIONS (AI contract). Research: PDF §11 "The 85 % rule" table + contradiction table, Figure 6B, Figure 7; DOCX §10.1, §10.3, §10.5, §10.7, §11.3 Table 18, §12.1, §13 Table 20.

## Owns
`com.mediflow.prescription/**` (sub-packages `draft, ai, sse, safety, sign`), `templates/prescription/**`, `messages/prescription_*`, V350–V399.

## Build
1. **Draft creation** (assigned doctor, from an encounter): typed text, photo upload (→ MinIO), or fully manual item editor.
2. **AI gateway**: `POST /api/prescriptions/{id}/ai-jobs` → returns **202 + jobId**; publish to `ai.rx.requests` (no patient identifiers); circuit breaker (Resilience4j) + health check; consume `ai.rx.results`.
3. **SSE** `GET /api/ai-jobs/{jobId}/events` pushes progress/result; client (`static/js/sse.js`) reconnects; if SSE drops → poll `GET /api/ai-jobs/{jobId}`; **after 10 s without result, UI switches to manual entry** with a clear message; late results are discarded.
4. **Per-item gate** (`ConfidenceGate`): item conf = min(drug, dose, frequency); threshold from `system_setting` (default 0.85, read-only here). ≥ threshold & no conflict → PREFILLED; < threshold → NEEDS_REVIEW (amber, AI guess beside original image); brand not in DGDA → UNKNOWN, blank, treated as < threshold.
5. **Contradiction engine** (`SafetyEngine`, deterministic, in-memory, p95 ≤ 300 ms, works on **generic** level using T08's dictionary tables):
   - Allergy conflict → CRITICAL (hard stop)
   - Severe drug–drug interaction (vs other items + active meds) → CRITICAL (change or override with reason + second confirmation)
   - Dose out of range (age/weight; `drug_dose_limit`) → CRITICAL
   - Duplicate therapy (same generic, two brands) → WARNING
   - Condition / pregnancy / vitals caution (e.g., NSAID + renal impairment, antihypertensive with low BP from latest vitals) → WARNING
   - Unknown item → UNKNOWN
   Runs on every draft change, for typed, AI and manual items alike.
6. **Review UI**: draft beside original photo; amber fields must be confirmed one by one; overrides need reason → `ai_flag_override`.
7. **E-sign**: blocked while any CRITICAL or unresolved NEEDS_REVIEW/UNKNOWN exists; requires licence number on practitioner + **MFA step-up** (T01 `MfaReauthService`); status SIGNED, immutable; publish `PrescriptionSigned`. **No auto-sign anywhere.**
8. **After signing**: addendum only (new linked prescription, pharmacy notified); **revoke** → ENTERED_IN_ERROR, notify doctor/pharmacy/patient, if already dispensed alert clinician, open incident record (24 h review flag).
9. **Patient copy**: readable e-prescription (brand + generic, dose, frequency, duration, Bangla/English), printable PDF/HTML with QR.
10. Metrics: per-item acceptance/correction counts (KPI "useful AI assistance").

## Acceptance
- Unit tests for every row of PDF "85 % rule" table and every contradiction type; test that signing is impossible with unresolved items; test 10-s fallback with a stubbed slow AI (Testcontainers RabbitMQ); test AI-down → manual; test revoke flow.
