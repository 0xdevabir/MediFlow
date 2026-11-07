# T03 — Hospital & doctor directory and search (D6, F2/F3/F7)

Read PLAN + CONVENTIONS. Research: PDF §5 (hospital website review + features F2, F3, F7), §8 (hospital, department, practitioner, practitioner_role, lab_test), §9 D6, risk "Hospital or doctor list outdated".

## Owns
`com.mediflow.directory/**`, `templates/directory/**`, `messages/directory_*`, V200–V249.

## Build
1. **Public** (no login) search: by hospital/branch, department (clinical departments only), doctor name; filters: hospital, department, date. Results show hospital card (address, opening hours, phone, **emergency & ambulance phone**, last-updated date) and doctor cards with next free slots.
2. **Slot generation** from `practitioner_role.schedule` (JSON weekly template: day, start, end, slot minutes) → materialise `slot` rows 14 days ahead (scheduled job) ; expose `DirectoryQueryService.freeSlots(practitionerRoleId, date)` for T04.
3. Admin CRUD (ROLE_ADMIN) for hospital, department, practitioner, practitioner_role/schedule, lab_test (`direct_allowed`, price shown before booking), lab_package (bundles of direct tests). (T11 links to these pages from the admin dashboard.)
4. Schedule change → publish event so T10 notifies already-booked patients.
5. Seed data (dev profile): 3 hospitals incl. branches, 6 departments, 10 doctors, direct tests (FBS/RBS, CBC, lipid profile, urine R/E, HbA1c) with LOINC codes, 1 "basic health check" package.
6. Bangla/English.

## Acceptance
- Search works unauthenticated; admin CRUD restricted; slot generation tested (no duplicates, respects schedule); packages only contain `direct_allowed` tests (validated).
