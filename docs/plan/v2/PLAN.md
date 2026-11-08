# MediFlow AI v2 — Plan

**Goal:** Make frontend and backend **production-quality** versus R&D PDF v1.3 (wins over Technical DOCX v1.0).  
**Input:** `docs/plan/v2/GAP-ANALYSIS.md` + merged T00–T14 codebase.  
**Mode:** Feature completion + design system. **No tests, no Docker** in v2 task runs (user rule).

---

## 1. Approach

1. **Correctness first** — close K1–K6 (real lab orders, ER assignee ACL, staff UI, shared drug dictionary, adherence widget, T14 deferred functional rows).  
2. **One design system** — Bootstrap 5 + Thymeleaf fragments + `app.css` tokens; every module page adopts shared chrome.  
3. **Role dashboards** — `/` becomes a role-aware hub; each role sees today’s work, not a generic welcome.  
4. **Shared contracts** — one owner task defines ports/events/CSS/layout; other tasks implement against those names without touching shared files.  
5. **Parallel agents** — 10 independent branches; file ownership is exclusive; cross-module needs go through ports defined in V2-01.

Preserve PLAN §6 safety invariants: AI never signs/dispenses; 85% per-item gate; critical conflict hard stop; deny-by-default access; audit on clinical read/write.

---

## 2. Frontend design direction

### 2.1 Stack (fixed)

- Server-rendered **Thymeleaf** + **Bootstrap 5.3** webjars  
- Minimal vanilla JS: shared `sse.js`, module scripts only where needed (`prescription/ai-job.js`, `appointment-queue.js`)  
- i18n: `messages/*_{en,bn}.properties` + existing `?lang=` toggles  
- No SPA framework

### 2.2 Visual system

| Token | Direction |
|-------|-----------|
| Brand | Extend existing teal (`--mf-teal` / dark). Clinical, calm, Bangladesh-clinic daylight — **not** purple gradients, cream-serif terracotta, or broadsheet hairlines |
| Surface | Soft layered background (subtle teal→slate wash or light grid), white content panels with 1px border — cards only for interactive worklists |
| Type | Distinctive pair: e.g. **Source Serif 4** (page titles) + **IBM Plex Sans** / **Noto Sans Bengali** for UI + Bangla body. Load via Google Fonts or self-host under `static/fonts/` |
| Status | Critical = deep rose; Urgent = amber; Normal = slate; AI-amber = review highlight; Success = teal |
| Motion | 2–3 intentional motions only: nav collapse, flash dismiss, SSE “pulse” on live badge — no decorative noise |
| Density | Clinical pages denser than marketing; patient pages airier |

### 2.3 Layout components (owned by V2-01 under `templates/layout/`)

| Fragment | Purpose |
|----------|---------|
| `base.html` | head, nav (role-aware), flash, scripts, skip-link, `lang` on `<html>` |
| `fragments/page-shell.html` | `page-header` (title, subtitle, primary actions), main width variants |
| `fragments/status-badge.html` | triage / Rx / lab / AI review badges |
| `fragments/empty-state.html` | zero-data worklists |
| `fragments/live-region.html` | `aria-live` wrapper for SSE |
| `fragments/role-dashboard.html` | tile/list pattern for role homes |
| `fragments/data-table.html` | responsive table → stacked rows on mobile |

### 2.4 Role dashboards (content owned by modules; shell + `/` routing by V2-01)

| Role | Home primary jobs |
|------|-------------------|
| PATIENT | Next appointment / token, book, direct lab, adherence widget, Rx & reports shortcuts |
| FRONT_DESK | Check-in, walk-in register, book, queue board, emergency flag |
| NURSE | Triage queue, vitals entry, call list, Red Alerts |
| DOCTOR | Today’s assigned patients, open consults, pending Rx drafts, lab results badge |
| ER | Active Red Alert board |
| LAB_TECH | Sample queue, verify pending |
| PHARMACIST | Dispense queue, low stock |
| ADMIN | Directory, staff, settings, audit, analytics |
| CLINICAL_APPROVER | Pending dual-approval queue only |

`HomeController` loads a small view-model (via ports / current roles) and returns `home` with role sections — modules supply optional fragments under their template folders that home includes by name.

### 2.5 Responsive / a11y / i18n / SSE

- Mobile-first: sticky primary CTA on vitals, dispense, e-sign; queue token large on phone  
- WCAG-minded: focus rings, labeled inputs, live regions for queue/AI/ER, contrast on status colors  
- Every new string: `en` + `bn`  
- SSE: shared reconnect helper already in `sse.js`; wrap status in live-region fragment; keep poll + 10 s manual fallback for AI

---

## 3. Backend completion approach

### 3.1 Shared ports (defined in V2-01 — others implement)

Exact names agents must code against:

```java
// com.mediflow.shared.port.LabOrderPort
public interface LabOrderPort {
  /** Creates a real doctor-ordered lab_order (or throws). Returns lab order id. */
  UUID requestOrder(UUID encounterId, UUID patientId, UUID hospitalId, String testCode);
}

// com.mediflow.shared.port.DrugDictionaryPort
public interface DrugDictionaryPort {
  record DrugMatch(/* brandId, brandName, strength, form, dgdaRegNo,
                      genericId, genericName, atc, rxnorm, matchType, score, illustrative */) {}
  record GenericRef(UUID id, String name, String atcCode, String rxnormCode, boolean illustrative) {}
  record InteractionRule(UUID genericAId, UUID genericBId, String severity, String description, boolean placeholder) {}
  record DoseLimitRule(/* genericId, population, route, min, max, unit, placeholder */) {}

  List<DrugMatch> findBrandProducts(String brandName);
  List<DrugMatch> suggestBrands(String query);
  Optional<GenericRef> findGeneric(UUID genericId);
  Optional<GenericRef> findGenericByName(String name);
  List<InteractionRule> interactionsAmong(Collection<UUID> genericIds);
  Optional<InteractionRule> interaction(UUID genericA, UUID genericB);
  List<DoseLimitRule> doseLimits(UUID genericId);
}

// com.mediflow.shared.port.AdherenceQueryPort
public interface AdherenceQueryPort {
  Optional<Integer> adherencePercent(UUID patientId);
}

// com.mediflow.shared.port.PatientQrPort
public interface PatientQrPort {
  String issueToken(UUID patientId, Duration ttl);
  Optional<UUID> resolvePatientId(String token);
}

// com.mediflow.shared.port.MfaReauthPort  (optional thin façade if not already injectable cross-module)
public interface MfaReauthPort {
  void requireFreshMfa(Duration maxAge);
}
```

**DomainEvents additions / promotions (V2-01):** ensure shared events exist for pharmacy dispense lifecycle if still local (`MedicationDispensed` already in CONVENTIONS — verify and document). Prefer publishing shared types over `PharmacyEvents`.

**AccessFacts / PatientAccessPort:** add/clarify  
`boolean isErAssigned(UUID erUserId, UUID patientId)` meaning *active red alert for patient with `assigned_er_user_id = erUserId` OR break-glass* (implementation in V2-02).

**Deprecations:** clinical `com.mediflow.clinical.consultation.port.LabOrderPort` becomes a deprecated typedef or is deleted after move; pharmacy `DrugDictionaryPort` interface moves to shared (impl class stays in pharmacy).

### 3.2 Wiring map

| Port | Interface owner | Implementation owner | Consumers |
|------|-----------------|----------------------|-----------|
| `LabOrderPort` | V2-01 | V2-08 (`@Primary` / `@Component`) | V2-06 clinical |
| `DrugDictionaryPort` | V2-01 | V2-09 | V2-07 Rx safety, V2-09 dispense |
| `AdherenceQueryPort` | V2-01 | V2-10 | V2-03 patient dashboard, V2-01 home |
| `PatientQrPort` | V2-01 | V2-03 | V2-09 pharmacy, V2-08 lab |
| `MfaReauthPort` | V2-01 | V2-02 | V2-07 e-sign |
| `PatientAccessPort` (fix ER) | already shared | V2-02 | all |

Until V2-08 ships, clinical keeps compiling against the shared interface; V2-01 may ship a **temporary** `@ConditionalOnMissingBean` that still no-ops **or** remove NoOp and require V2-08 merge first — prefer: V2-01 moves interface + leaves thin NoOp under `shared.port.stub` only for boot; V2-08 replaces immediately on merge.

### 3.3 Feature completion themes

- **Care path integrity:** consult → real lab order → tech queue → report → doctor badge  
- **Emergency ACL:** assignee column drives ER_ASSIGNED  
- **Pilot readiness:** staff CRUD UI  
- **Single drug truth:** shared dictionary port  
- **Adherence loop:** follow-up fields publish events; patient sees widget  
- **FHIR/AI polish:** config-driven base URL; dictionary export remains T08→T09 hourly  

---

## 4. Parallel execution

```
         ┌─ V2-02 identity-staff-access
         ├─ V2-03 patient-dashboard
         ├─ V2-04 directory-polish
         ├─ V2-05 appointment-queue-er
V2-01 ───┼─ V2-06 clinical-consultation     (code against LabOrderPort by name)
shared   ├─ V2-07 prescription-ux           (code against DrugDictionaryPort / MfaReauthPort)
ports +  ├─ V2-08 lab-order-port            (implements LabOrderPort)
design   ├─ V2-09 pharmacy-dictionary       (implements DrugDictionaryPort)
         ├─ V2-10 notifications-adherence   (implements AdherenceQueryPort)
         ├─ V2-11 admin-approver-audit
         └─ V2-12 fhir-ai-polish
```

**Merge order recommendation (after parallel build):** V2-01 → V2-08 & V2-09 & V2-02 & V2-10 → consumers (V2-06, V2-07, V2-03) → V2-04/05/11/12. Agents may develop in parallel against V2-01 interface stubs on a shared branch tip that already contains V2-01, **or** against interface names documented here before V2-01 lands (compile will fail until V2-01 merges — prefer merge V2-01 first if coordination allows). For true parallel start: V2-01 lands first as Wave 0 of v2; remaining nine run in parallel.

**Practical wave:**

| Wave | Tasks |
|------|-------|
| v2-W0 | V2-01 |
| v2-W1 | V2-02 … V2-12 (parallel) |

---

## 5. Flyway ranges (v2)

| Task | Range |
|------|-------|
| V2-01 | V650–V659 |
| V2-02 | V660–V669 |
| V2-03 | V670–V679 |
| V2-04 | V680–V689 |
| V2-05 | V690–V699 |
| V2-06 | V700–V709 |
| V2-07 | V710–V719 |
| V2-08 | V720–V729 |
| V2-09 | V730–V739 |
| V2-10 | V740–V749 |
| V2-11 | V750–V759 |
| V2-12 | V760–V769 |

`out-of-order: true` remains in dev.

---

## 6. Ownership rules (v2)

- **Exactly one task** owns: `com.mediflow.shared/**`, `com.mediflow.config/**`, `templates/layout/**`, `templates/home.html`, `templates/login.html`, `templates/coming-soon.html`, `templates/error/**`, `static/css/**`, `static/js/sse.js`, `pom.xml`, `application.yml`, `application-dev.yml`, `messages/common_*` → **V2-01**.  
- No two tasks own the same path. Module package ↔ `templates/<module>/` ↔ `messages/<module>_*` stay aligned.  
- `static/js/prescription/**` → V2-07; `static/js/appointment-queue.js` → V2-05.  
- Do not edit another task’s files; if blocked, extend a **shared port** (request via PR comment; V2-01 amends interface).  
- No tests / no Docker / no secrets in commits.

---

## 7. Task index

See `docs/plan/v2/INDEX.md` and `docs/plan/tasks/V2-NN-*.md`.

---

## 8. Definition of done (v2 overall)

- K1–K6 closed in code.  
- Every role has a useful home dashboard section.  
- Shared design fragments used by all module templates touched in v2.  
- EN+BN strings for new UI.  
- Lab order from consultation creates a `lab_order` row visible in lab queue.  
- ER user without assignment cannot read unrelated Red-Alert patients.  
- Admin can create/disable staff and assign roles from UI.  
- Rx safety and pharmacy resolve brands through `DrugDictionaryPort`.  
- Patient home/profile shows adherence percent via `AdherenceQueryPort`.  
- T14 deferred **functional** rows dispositioned (not test/Docker rows).
