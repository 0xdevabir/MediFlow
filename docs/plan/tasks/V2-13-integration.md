# V2-13 — v2 integration pass

## Owns
Everything (it runs alone, after V2-01…V2-12 have been merged into main), including shared/, layout, static assets, pom.xml and application*.yml.

## Build
1. Read every docs/plan/reports/V2-*.md "Open issues" and every docs/plan/requests/V2-*.md. Resolve every cross-module request and every "waits on V2-xx / until V2-01 wires …" item, now that all the modules are on main:
   - wire the role home tiles/fragments (admin, desk, nurse, doctor, ER, lab, pharmacist, patient, clinical approver) to real data and links, with no placeholder or "configure" copy;
   - the patient next-visit glance from appointments; the adherence % via AdherenceQueryPort;
   - the CLINICAL_APPROVER vs ADMIN dashboard split;
   - promote the PharmacyEvents / DirectoryScheduleChangeDetail events requested; notification consumers for directory schedule changes; FollowUpScheduled publishers;
   - remove dead duplicate ports and stubs that the real shared beans now replace (keep the safe fallbacks only where no implementation exists).
2. Sweep all the Thymeleaf templates: every page uses the V2-01 layout/design-system classes, has bn + en message keys (no missing keys in messages_bn.properties vs messages.properties), every nav link resolves to a real controller (no coming-soon for built modules), and mobile-friendly tables/forms.
3. Make sure that ./mvnw -q -DskipTests test-compile passes.
4. Write docs/plan/reports/V2-13.md.

## Acceptance
- No open cross-module request left unaddressed (each is listed in the report as done, or with a reason).
- No nav link to a placeholder for a built module; message bundles in parity.
- Compiles.
