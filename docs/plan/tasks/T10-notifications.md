# T10 — Notifications & reminders, Bangla/English (M8)

Read PLAN + CONVENTIONS (events table). Research: PDF §6 "Follow-up & reminders", §9.2 flows 28–30; DOCX Table 5, Table 19 R7.

## Owns
`com.mediflow.notification/**`, `templates/notification/**`, `messages/notification_*`, V500–V549.

## Build
1. Implement `NotificationPort`: channels SMS (pluggable gateway interface + logging dev impl; adapter for a BD gateway via HTTP config), email (Spring Mail → Mailpit in dev), web push (FCM adapter, stub in dev), and **in-app** notifications (bell + list page).
2. Async delivery through RabbitMQ `notify.*` queues with retries + exponential backoff; on final failure escalate to next channel and add to "staff call list" page (risk R7).
3. Templates per event, per locale (`bn`, `en`) using user's preferred language.
4. Listeners for every event in CONVENTIONS (booking confirm, reminder 24 h + 2 h before, queue "you're next", Red Alert to ER/duty doctor/nurse — high priority, report ready, "please see a doctor", Rx signed/revoked, dispensed, schedule changed).
5. **Reminder scheduler**: from signed Rx (dose times by frequency for duration) and follow-up dates; patient acknowledges → adherence log; adherence % on patient dashboard.
6. Patient notification preferences (channels, quiet hours, language).

## Acceptance
- Tests: event → correct template/locale/recipients; retry + escalation; reminder schedule generation for OD/BD/TDS × duration; acknowledgement updates adherence.
