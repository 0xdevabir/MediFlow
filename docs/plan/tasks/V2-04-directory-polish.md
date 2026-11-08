# V2-04 — Directory polish (search + admin UX)

**Wave:** v2-W1  
**Branch:** `feat/V2-04-directory-polish`  
**Depends on (by name):** V2-01 layout fragments; anonymous directory nav from V2-01

## Owns

- `backend/src/main/java/com/mediflow/directory/**`
- `backend/src/main/resources/templates/directory/**`
- `backend/src/main/resources/messages/directory_en.properties`
- `backend/src/main/resources/messages/directory_bn.properties`
- Flyway `V680__*` … `V689__*`

## Build

1. Apply design system to public search and admin CRUD forms (hospital, department, practitioner, role/schedule, test, package).
2. Hospital card: emergency phone, hours, last-updated cue (R&D F2/F7).
3. Improve search UX: department / doctor / branch filters; free-slot clarity for booking handoff to `/appointments`.
4. Direct-test list + packages presentation for patients (cost note if fee fields exist; otherwise omit).
5. If JPA entities drift from JDBC columns under `ddl-auto=validate`, map missing columns (only if required).
6. Optional: publish richer data for `PractitionerScheduleChanged` consumers (no notification package edits).
7. Home/admin tile fragment under `directory/fragments` for ADMIN directory shortcut.
8. EN+BN strings.

## Acceptance

- Directory search and admin forms visually consistent with V2-01 system.
- Emergency number visible on hospital detail/card.
- No edits outside Owns; booking still deep-links to appointment module paths.
