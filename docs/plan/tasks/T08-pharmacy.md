# T08 — Pharmacy dispensing & DGDA drug dictionary (M6)

Read PLAN + CONVENTIONS. Research: PDF §10 "Brand-first medicine mapping (DGDA)", §8 drug_brand/drug_generic; DOCX Table 19 R10, Table 18 last rows.

## Owns
`com.mediflow.pharmacy/**` (sub-packages `dictionary, dispensing, stock`), `templates/pharmacy/**`, `messages/pharmacy_*`, V450–V499.

## Build
1. **Drug dictionary**: generic (name, ATC, RxNorm), brand (name, DGDA reg no, strength, form, generic). CSV importer for the DGDA register (admin upload; versioned import, report of unmapped rows). Seed sample: Napa→Paracetamol N02BE01, Seclo→Omeprazole A02BC01, Ciprocin→Ciprofloxacin J01MA02 + ~50 common BD brands **marked as illustrative**.
2. Seed `drug_interaction` (severity) and `drug_dose_limit` (adult/paediatric mg/kg, max daily) small curated set, clearly marked "placeholder — needs licensed source".
3. `DrugDictionaryPort` implementation: exact + fuzzy lookup (pg_trgm), brand→generic; cached in Redis; also export endpoint `GET /api/internal/dictionary/export` (service-to-service auth) used by T09 to build its matcher.
4. **Dispensing**: pharmacist scans patient QR / verifies 2 identifiers, sees **signed** prescriptions only (read-only, cannot edit), validates each item, checks stock, dispenses (batch, expiry) → `dispense` rows, publish `MedicationDispensed`. "Send back to doctor" with reason (any doubt).
5. Out of stock: substitution only with doctor approval (request → doctor approves in T06 UI via event) or direct patient to another pharmacy.
6. Stock: simple per-pharmacy stock levels, low-stock list.
7. React to `PrescriptionRevoked` (block dispensing, alert if already dispensed).

## Acceptance
- Tests: two brands of same generic resolve to same generic; unknown brand → empty (never guessed); revoked Rx cannot be dispensed; pharmacist cannot modify items; identity check required.
