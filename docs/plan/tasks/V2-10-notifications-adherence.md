# V2-10 — Notifications, adherence port, schedule blast

**Wave:** v2-W1  
**Branch:** `feat/V2-10-notifications-adherence`  
**Depends on (by name):** V2-01 `AdherenceQueryPort`; consumers V2-03 / V2-01 home

## Owns

- `backend/src/main/java/com/mediflow/notification/**`
- `backend/src/main/resources/templates/notification/**`
- `backend/src/main/resources/messages/notification_en.properties`
- `backend/src/main/resources/messages/notification_bn.properties`
- Flyway `V740__*` … `V749__*`

## Build

1. Implement **`AdherenceQueryPort`** delegating to `ReminderService.adherencePercent` (and expose anything the adherence fragment needs).
2. Ensure `notification/fragments :: adherence` is production-ready (model attr `pct`, BN/EN, design-system card — not Bootstrap-default-only). Document required model attributes in fragment comment for V2-03.
3. **`PractitionerScheduleChanged` / `ScheduleChanged`:** resolve affected appointments and notify patients (upgrade log-only listener) using existing recipient lookup; add helper methods in notification repos only.
4. Listen for `FollowUpScheduled` (already) — verify medication/visit reminders are created when clinical/Rx publish.
5. Inbox / bell / reminders / call-list / preferences → design system + a11y (bell `aria-live` polite).
6. Keep channel stubs (`LoggingPushGateway`) unless SMS/email already wired — do not add Docker.
7. EN+BN.

## Acceptance

- `AdherenceQueryPort` bean returns percent consistent with reminders page.
- Adherence fragment includable from patient templates without editing patient package.
- Schedule-change path notifies at least one resolvable booked patient when data exists (or clearly no-ops when none).
- No ownership violations.
