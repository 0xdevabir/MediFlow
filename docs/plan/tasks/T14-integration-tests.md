# T14 — Integration, end-to-end & security validation (Wave 3)

Run last. Read PLAN, CONVENTIONS, all `docs/plan/reports/*.md` and `docs/plan/requests/*.md`. Research: PDF §14 test table; DOCX §15 Table 22, §12 risk register.

## Owns
`backend/src/test/java/com/mediflow/e2e/**`, `e2e/**` (Playwright), `load/**` (k6), `docs/plan/reports/T14.md`. May fix integration bugs anywhere, small and well-described.

## Build
1. Resolve all open request files (apply or document why not).
2. **E2E (Playwright)** for the four care paths: standard OPD, urgent OPD, Critical → Red Alert → ER, direct lab test (normal + abnormal → see-a-doctor → booking). Plus full Rx path: photo → AI → review amber → sign with MFA → pharmacist dispense → patient copy → reminder.
3. **Safety drills**: AI service stopped → manual mode; AI slow (> 10 s) → manual; SSE blocked → polling; critical vitals in OPD → auto Red Alert; notification channel failure → escalation; revoke after dispense.
4. **RBAC matrix**: HTTP-level tests for every cell (7 roles + ER × resources) — expect 403/200 accordingly.
5. ZAP baseline scan; fix High/Medium.
6. k6: 200 concurrent users booking/queue; AI p95 ≤ 5 s, rule engine ≤ 300 ms, API p95 ≤ 500 ms. Record numbers.
7. Report with KPIs, failures, and remaining gaps.

## Acceptance
- All E2E + RBAC tests green in CI; report written.
