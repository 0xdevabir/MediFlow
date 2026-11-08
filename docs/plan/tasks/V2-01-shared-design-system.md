# V2-01 — Shared contracts & design system

**Wave:** v2-W0 (blocking for consumers)  
**Branch:** `feat/V2-01-shared-design-system`  
**Read:** `docs/plan/v2/PLAN.md`, `docs/plan/v2/GAP-ANALYSIS.md`, R&D §7 / §12, existing `layout/base.html`

## Owns

Exclusive paths (no other v2 task may edit these):

- `backend/src/main/java/com/mediflow/shared/**`
- `backend/src/main/java/com/mediflow/config/**`
- `backend/pom.xml` (and root `pom.xml` if present)
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/templates/layout/**`
- `backend/src/main/resources/templates/home.html`
- `backend/src/main/resources/templates/login.html`
- `backend/src/main/resources/templates/coming-soon.html`
- `backend/src/main/resources/templates/error/**`
- `backend/src/main/resources/static/css/**`
- `backend/src/main/resources/static/js/sse.js`
- `backend/src/main/resources/messages/common_en.properties`
- `backend/src/main/resources/messages/common_bn.properties`
- Flyway `V650__*` … `V659__*` under `backend/src/main/resources/db/migration/`

## Build

1. **Move / define shared ports** (exact FQCN):
   - `com.mediflow.shared.port.LabOrderPort` — `UUID requestOrder(UUID encounterId, UUID patientId, UUID hospitalId, String testCode)`
   - `com.mediflow.shared.port.DrugDictionaryPort` — API as in `PLAN.md` §3.1 (match current pharmacy service capabilities)
   - `com.mediflow.shared.port.AdherenceQueryPort` — `Optional<Integer> adherencePercent(UUID patientId)`
   - `com.mediflow.shared.port.PatientQrPort` — `issueToken` / `resolvePatientId`
   - `com.mediflow.shared.port.MfaReauthPort` — `requireFreshMfa(Duration maxAge)`
2. Leave `@ConditionalOnMissingBean` **NoOp** only for `LabOrderPort` under `shared.port.stub` (log + publish `DomainEvents.LabOrderRequested`, return nil UUID) so the app boots before V2-08; delete clinical’s duplicate interface/NoOp **or** make clinical types deprecated aliases that extend/implement the shared port (clinical package edits are V2-06 — coordinate: prefer delete-by-V2-06; V2-01 only adds shared types).
3. Document port Javadoc: implementors = V2-08 / V2-09 / V2-10 / V2-03 / V2-02.
4. **Design system CSS** in `static/css/app.css` (+ optional `tokens.css`): color/status tokens, type scale, spacing, form controls, worklist rows, mobile utilities; load fonts for Latin + Bengali.
5. **Layout fragments** listed in PLAN §2.3; upgrade `base.html`: skip-link, `lang` binding, role-aware nav (staff users, audit, anonymous directory affordance, clinical-approver cue), flash + live-region hooks, include design CSS.
6. **Role home:** `HomeController` + `home.html` compose role sections via fragments other modules will add (`th:replace` optional blocks). Provide empty-state placeholders per role until modules fill them. Inject `AdherenceQueryPort` for PATIENT tile when bean present.
7. **Security cookie polish:** session already Strict; set CSRF cookie `SameSite=Strict` / `HttpOnly` as appropriate in `SecurityConfig` without breaking Thymeleaf forms.
8. Yml: document `mediflow.patient.qr-token-ttl`, notification/audit keys already present; add any design-unrelated shared props needed by ports.
9. Retire or redirect `/modules/{module}` coming-soon to real hubs where known; keep 404 for unknown.
10. Optional: add Chart.js webjar dependency on `pom.xml` for V2-06 to use (V2-06 must not edit pom).

## Acceptance

- Shared port interfaces compile and are the **only** cross-module lab/dictionary/adherence/QR/MFA façades named in PLAN.
- `layout/base.html` + fragments render without module-specific hardcoded hacks.
- `/` shows distinct sections by role (even if some tiles say “configure module”).
- `app.css` exposes documented CSS variables for status + brand; login/home use the system.
- No secrets committed; no test/Docker changes required.
