# V2-05 — Appointments, queue, Red Alert ER UX

**Wave:** v2-W1  
**Branch:** `feat/V2-05-appointment-queue-er`  
**Depends on (by name):** V2-01 layout + `sse.js`; V2-02 consumes assignee column you already write

## Owns

- `backend/src/main/java/com/mediflow/appointment/**`
- `backend/src/main/resources/templates/appointment/**`
- `backend/src/main/resources/templates/queue/**`
- `backend/src/main/resources/templates/er/**`
- `backend/src/main/resources/static/js/appointment-queue.js`
- `backend/src/main/resources/messages/appointment_en.properties`
- `backend/src/main/resources/messages/appointment_bn.properties`
- Flyway `V690__*` … `V699__*`

## Build

1. **Always set ER assignee** on raise: prefer on-duty / round-robin among `ROLE_ER` for hospital; never leave null when an ER user exists (supports V2-02 ACL).
2. Front-desk / nurse UX: check-in, emergency flag, triage, queue board — adopt `page-shell`, status badges (CRITICAL/URGENT/NORMAL), mobile token view.
3. Patient queue page: large token, patients-ahead, SSE via shared live-region pattern (`appointment-queue.js` + `sse.js`).
4. ER board/detail: priority visual hierarchy; acknowledge + handover forms accessible; show assignee name.
5. Doctor queue call UI polish; keep publishing shared `QueueCalled` / existing domain events.
6. Soft: if easy, prefer directory slot port over local `SlotCatalog` without editing directory package (inject only shared/directory **public** API already exposed — if that requires directory edits, skip and document).
7. Role fragments for FRONT_DESK / NURSE / ER / DOCTOR home tiles (today’s queue counts).
8. EN+BN.

## Acceptance

- New Red Alerts persist `assigned_er_user_id` when ER staff exist.
- Queue/ER/appointment templates use design system; SSE token updates remain functional.
- No ownership violations; no test/Docker work.
