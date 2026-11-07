package com.mediflow.identity.access;

import static com.mediflow.identity.access.AccessAction.C;
import static com.mediflow.identity.access.AccessAction.D;
import static com.mediflow.identity.access.AccessAction.O;
import static com.mediflow.identity.access.AccessAction.R;
import static com.mediflow.identity.access.AccessAction.S;
import static com.mediflow.identity.access.AccessAction.U;
import static com.mediflow.identity.access.AccessAction.V;

import com.mediflow.shared.domain.Role;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The static RBAC matrix of R&D v1.3 §12 (newer than the Technical doc Table 17 and wins where they
 * differ: nurse demographics R*, nurse vitals R C, front-desk direct-test orders C⁶, patient lab
 * orders R C⁶). ER is "doctor-level access limited to the Red-Alert patient they are assigned".
 * CLINICAL_APPROVER has no matrix cells (it only co-approves rule/threshold changes, see T11).
 *
 * <p>Footnote markers kept as {@link Cell#minimumNecessary()} (R* and R†) and {@link
 * Cell#footnote()} so callers can shape the returned data.
 */
public final class AccessMatrix {

  /** One granted cell. */
  public record Cell(Set<AccessAction> actions, AccessScope scope, boolean minimumNecessary, String footnote) {}

  private static final Map<AccessResource, Map<Role, Cell>> MATRIX = new EnumMap<>(AccessResource.class);

  static {
    // Demographics: Patient R U¹ | Doctor R | Lab R* | Pharm R* | Nurse R* | Front desk R C U | Admin —
    row(AccessResource.DEMOGRAPHICS)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, "1", R, U))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R))
        .put(Role.ROLE_LAB_TECH, cell(AccessScope.LINKED_LAB_ORDER, true, "*", R))
        .put(Role.ROLE_PHARMACIST, cell(AccessScope.LINKED_PRESCRIPTION, true, "*", R))
        .put(Role.ROLE_NURSE, cell(AccessScope.STAFF, true, "*", R))
        .put(Role.ROLE_FRONT_DESK, cell(AccessScope.STAFF, false, null, R, C, U));
    // History & allergies: R C² | R C U | — | R† | — | — | —
    row(AccessResource.HISTORY_ALLERGIES)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, "2", R, C))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R, C, U))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R, C, U))
        .put(Role.ROLE_PHARMACIST, cell(AccessScope.LINKED_PRESCRIPTION, true, "†", R));
    // Vital signs: R C² | R C U | — | — | R C | — | —
    row(AccessResource.VITALS)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, "2", R, C))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R, C, U))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R, C, U))
        .put(Role.ROLE_NURSE, cell(AccessScope.STAFF, false, null, R, C));
    // Appointments & queue: R C | R U | — | — | R U | R C U | R‡
    row(AccessResource.APPOINTMENTS)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, null, R, C))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R, U))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R, U))
        .put(Role.ROLE_NURSE, cell(AccessScope.STAFF, false, null, R, U))
        .put(Role.ROLE_FRONT_DESK, cell(AccessScope.STAFF, false, null, R, C, U))
        .put(Role.ROLE_ADMIN, cell(AccessScope.AGGREGATE, false, "‡", R));
    // Hospital & test directory: R for everyone, admin C U⁵
    Row directory = row(AccessResource.DIRECTORY);
    for (Role role :
        EnumSet.of(
            Role.ROLE_PATIENT,
            Role.ROLE_DOCTOR,
            Role.ROLE_ER,
            Role.ROLE_LAB_TECH,
            Role.ROLE_PHARMACIST,
            Role.ROLE_NURSE,
            Role.ROLE_FRONT_DESK)) {
      directory.put(role, cell(AccessScope.GLOBAL, false, null, R));
    }
    directory.put(Role.ROLE_ADMIN, cell(AccessScope.GLOBAL, false, "5", C, U));
    // Consultation notes: R³ | R C U | — ...
    row(AccessResource.CONSULTATION_NOTES)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, true, "3", R))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R, C, U))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R, C, U));
    // Draft prescription: — | R C U | — ...
    row(AccessResource.DRAFT_PRESCRIPTION)
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R, C, U))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R, C, U));
    // Signed e-prescription: R | R S | — | R D | — | — | —
    row(AccessResource.SIGNED_PRESCRIPTION)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, null, R))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R, S))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R, S))
        .put(Role.ROLE_PHARMACIST, cell(AccessScope.LINKED_PRESCRIPTION, false, null, R, D));
    // Lab orders: R C⁶ | R C | R U | — | R | C⁶ | —
    row(AccessResource.LAB_ORDERS)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, "6", R, C))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R, C))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R, C))
        .put(Role.ROLE_LAB_TECH, cell(AccessScope.LINKED_LAB_ORDER, false, null, R, U))
        .put(Role.ROLE_NURSE, cell(AccessScope.STAFF, false, null, R))
        .put(Role.ROLE_FRONT_DESK, cell(AccessScope.STAFF, false, "6", C));
    // Lab reports: R | R | R C U V | — ...
    row(AccessResource.LAB_REPORTS)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, null, R))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R))
        .put(Role.ROLE_LAB_TECH, cell(AccessScope.LINKED_LAB_ORDER, false, null, R, C, U, V));
    // Dispensing record: R | R | — | R C U | — ...
    row(AccessResource.DISPENSING)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, null, R))
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R))
        .put(Role.ROLE_PHARMACIST, cell(AccessScope.LINKED_PRESCRIPTION, false, null, R, C, U));
    // AI flags & override log: — | R O | — | R | — | — | R‡
    row(AccessResource.AI_FLAGS)
        .put(Role.ROLE_DOCTOR, cell(AccessScope.ASSIGNED, false, null, R, O))
        .put(Role.ROLE_ER, cell(AccessScope.ER_ASSIGNED, false, null, R, O))
        .put(Role.ROLE_PHARMACIST, cell(AccessScope.LINKED_PRESCRIPTION, false, null, R))
        .put(Role.ROLE_ADMIN, cell(AccessScope.AGGREGATE, false, "‡", R));
    // Audit log: R⁴ | — ... | Admin R
    row(AccessResource.AUDIT_LOG)
        .put(Role.ROLE_PATIENT, cell(AccessScope.OWN, false, "4", R))
        .put(Role.ROLE_ADMIN, cell(AccessScope.GLOBAL, false, null, R));
    // Settings & users: admin C U⁵ only
    row(AccessResource.SETTINGS_USERS).put(Role.ROLE_ADMIN, cell(AccessScope.GLOBAL, false, "5", C, U));
  }

  private AccessMatrix() {}

  public static Optional<Cell> cell(AccessResource resource, Role role) {
    return Optional.ofNullable(MATRIX.get(resource).get(role));
  }

  /** Whether the role holds the action on the resource at all (ignoring patient scope). */
  public static boolean grants(AccessResource resource, Role role, AccessAction action) {
    return cell(resource, role).map(c -> c.actions().contains(action)).orElse(false);
  }

  private static Row row(AccessResource resource) {
    return new Row(MATRIX.computeIfAbsent(resource, r -> new EnumMap<>(Role.class)));
  }

  private record Row(Map<Role, Cell> cells) {
    Row put(Role role, Cell cell) {
      cells.put(role, cell);
      return this;
    }
  }

  private static Cell cell(AccessScope scope, boolean minimum, String footnote, AccessAction... actions) {
    EnumSet<AccessAction> set = EnumSet.noneOf(AccessAction.class);
    Collections.addAll(set, actions);
    return new Cell(Collections.unmodifiableSet(set), scope, minimum, footnote);
  }
}
