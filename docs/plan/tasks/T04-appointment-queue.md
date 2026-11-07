# T04 — Appointments, check-in, queue token, triage, Red Alert & ER (M2, process 2.0 + 8.0)

Read PLAN + CONVENTIONS. Research: PDF §6.2 "How the emergency decision works" + "Who is seen first?" table, Figure 1, §9.2 flows 3–16, 29; DOCX Table 19 R6.

## Owns
`com.mediflow.appointment/**` (sub-packages `booking, queue, triage, redalert`), `templates/appointment/**`, `templates/queue/**`, `templates/er/**`, `messages/appointment_*`, V250–V299.

## Build
1. **Booking**: patient (or front desk on behalf) picks doctor + free slot (from T03 `DirectoryQueryService`), optimistic locking to prevent double-booking; cancel/reschedule; publish `AppointmentBooked/Cancelled`.
2. **Check-in** (front desk): verify identity (2 identifiers / QR), create `queue_entry` with **token number** per doctor per day, create `encounter` shell, publish `AppointmentCheckedIn`.
3. **Emergency flag**: front desk can flag suspected emergency at booking/arrival.
4. **Triage** (nurse): choose level CRITICAL / URGENT / NORMAL + symptom severity. No formula.
   - CRITICAL → `RedAlertService.raise()`: red_alert row, ER team + duty doctor notified with patient summary, patient **removed from OPD queue**, ER role gets scoped access (T01).
   - URGENT → ahead of NORMAL; among urgent: BORDERLINE vitals first, then arrival time.
   - NORMAL → FIFO by slot.
5. **Auto-escalation**: listen to `VitalsClassified`; patient level CRITICAL → Red Alert automatically even if nurse chose otherwise.
6. **Nurse override**: drag/reorder or move-up actions; reason required; every change audited.
7. **Live queue**: patient page shows token + "N patients ahead" (SSE, fallback polling 10 s); front-desk queue board; doctor "call next".
8. **ER view** (ROLE_ER): active Red Alerts, patient summary, acknowledge, handover status (admitted / referred / discharged) → closes alert; flows out of scope after.
9. Bangla/English.

## Acceptance
- Unit tests for queue ordering rules (all combinations), auto-escalation, override logging, double-booking prevention, CRITICAL never appears in OPD queue.
