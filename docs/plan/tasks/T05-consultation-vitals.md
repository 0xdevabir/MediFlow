# T05 — Consultation & vitals classification (M3)

Read PLAN + CONVENTIONS. Research: PDF §11 "Vital-sign classification: one design for every vital" + formulas table + rule table, Figure 8; DOCX §10.4 Table 14, §11.3 Table 18.

## Owns
`com.mediflow.clinical/**` (sub-packages `vitals, consultation`), `templates/clinical/**`, `messages/clinical_*`, V300–V349.

## Build
1. **Polymorphic vitals** (the core novelty — follow exactly):
   - `abstract class VitalSign { VitalType type(); static VitalSign parse(VitalType, String raw); VitalClass classify(VitalRuleSet rules); }`
   - `NumericVital` (HR, RR, TEMP, SPO2), `BloodPressureVital` parses "120/80".
   - All limits in `vital_rule` table (type, population ADULT/PAEDIATRIC/PREGNANCY, normal/borderline/critical ranges). Seed adult defaults from PDF rule table exactly. **Adding a new vital (e.g., glucose) = add rows, no code** — prove with a test using a rule-only vital.
   - Derived: MAP = (SBP + 2·DBP)/3 → < 65 CRITICAL; pulse pressure (info); shock index HR/SBP ≥ 0.9 BORDERLINE, ≥ 1.0 CRITICAL.
   - Patient level = max of all levels. Publish `VitalsClassified`.
   - Rules cached in Redis; admin edits (T11) evict cache.
2. **Vitals entry** (nurse): form for BP, HR, temp, SpO2, RR; immediate colour-coded classification; saved as `vital_observation` (LOINC + UCUM codes).
3. **Trend chart**: per vital over time (Chart.js via webjar or inline SVG), on doctor's consultation page.
4. **Consultation** (assigned doctor only, patient checked in or referral): history, allergies (confirm unverified ones), active meds, vitals trend, lab reports (read via repo + `@access` checks); diagnosis with **ICD-10 search** (seed a subset CSV), notes **versioned** (edits create new version); patient-readable summary.
5. "Order lab test" button → calls T07's service via event `LabOrderRequested` or port (define in request if missing). "Write prescription" → link to T06 with encounterId.
6. Listen to `LabReportVerified` → doctor notification badge.

## Acceptance
- Exhaustive boundary tests for every rule-table edge (e.g., HR 59/60/100/101/129/130/39/40, SpO2 90/91/94/95, BP 179/180, systolic 89/90), MAP and shock-index rules, BP text parsing errors, max-level aggregation.
