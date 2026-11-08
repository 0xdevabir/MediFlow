# V2-08 — Lab: real LabOrderPort + consult integration

**Wave:** v2-W1  
**Branch:** `feat/V2-08-lab-order-port`  
**Depends on (by name):** V2-01 `com.mediflow.shared.port.LabOrderPort`; V2-01 `PatientQrPort` (optional scan)

## Owns

- `backend/src/main/java/com/mediflow/lab/**`
- `backend/src/main/resources/templates/lab/**`
- `backend/src/main/resources/messages/lab_en.properties`
- `backend/src/main/resources/messages/lab_bn.properties`
- Flyway `V720__*` … `V729__*`

## Build

1. **Implement `@Component` / `@Primary` `LabOrderPort`** in lab package:
   - Resolve `testCode` → `lab_test` (LOINC/code/name).
   - Create `lab_order` with type DOCTOR, link `encounter_id`, patient, hospital, status appropriate for queue.
   - Publish `DomainEvents.LabOrderCreated` (and keep compatibility with `LabOrderRequested` if still needed).
   - Return real order UUID (never `UUID(0,0)`).
2. Ensure doctor-ordered path from `/lab/encounters/{id}/orders` and the port share the same service method.
3. Consultation deep-link: encounter orders list page stable URL for V2-06 to link.
4. Move lab authorization toward `@access.canReadLabOrder` / AccessPolicy where `LabAccess` duplicates; keep minimum-necessary.
5. UX: hub, queue, tech-order, reports — design system; abnormal “see a doctor” CTA links to booking.
6. Optional: identity confirm via `PatientQrPort` at sample collect.
7. Lab tech / patient / front-desk home fragments; EN+BN.

## Acceptance

- Calling `LabOrderPort.requestOrder(...)` inserts a row visible on lab queue for that hospital.
- Shared NoOp bean backs off (`@ConditionalOnMissingBean` / `@Primary` as appropriate).
- Lab templates use shared layout; critical/abnormal messaging intact.
