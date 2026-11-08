# V2-07 — Prescription UX, shared dictionary consumer, follow-up

**Wave:** v2-W1  
**Branch:** `feat/V2-07-prescription-ux`  
**Depends on (by name):** V2-01 `DrugDictionaryPort`, `MfaReauthPort`; V2-09 implements dictionary; V2-02 implements MFA port

## Owns

- `backend/src/main/java/com/mediflow/prescription/**`
- `backend/src/main/resources/templates/prescription/**`
- `backend/src/main/resources/static/js/prescription/**`
- `backend/src/main/resources/messages/prescription_en.properties`
- `backend/src/main/resources/messages/prescription_bn.properties`
- Flyway `V710__*` … `V719__*`

## Build

1. **Adopt `DrugDictionaryPort`** for brand search / interactions / dose limits used by `SafetyEngine` / item editor. Remove or adapt `RxDrugDictionary` / `RepositoryRxDrugDictionary` to a thin adapter over the shared port (adapter may live in prescription package).
2. Use **`MfaReauthPort`** for e-sign step-up instead of reaching into identity types if currently coupled.
3. Honor **`GET /prescriptions?encounterId=`** (and POST create-from-encounter) end-to-end; align list/new templates (no raw UUID-only UX for doctors coming from consult).
4. AI review UI: design-system amber fields, original image beside draft, live-region for SSE/`ai-job.js`; keep 10 s → manual fallback.
5. Optional follow-up date on signed Rx / draft → publish `FollowUpScheduled` when set.
6. Patient-readable signed copy polish (brand + generic, BN/EN).
7. Keep in-module circuit breaker **or** switch to Resilience4j only if V2-01 added the dependency — do not edit `pom.xml`.
8. Doctor home fragment: drafts needing review; EN+BN.

## Acceptance

- Safety checks resolve drugs via `DrugDictionaryPort` (works when V2-09 bean present).
- Deep link from consultation with `encounterId` opens/creates the right draft flow.
- E-sign still requires MFA step-up + licence; AI never auto-signs.
- Prescription templates/JS use shared SSE helpers and design system.
