package com.mediflow.identity.access;

import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.ClockPort;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reusable {@code @PreAuthorize} bean, e.g. {@code @PreAuthorize("@access.canReadPatient(#patientId)")}
 * or {@code @PreAuthorize("@access.can('VITALS','C',#patientId)")}.
 *
 * <p>Deny by default: no authentication, unknown resource/action, a role without the cell, or a
 * patient outside the role's scope all return {@code false}. A user with several roles is allowed
 * if any role allows. A {@code null} patient id asks for list-level access (e.g. a lab worklist);
 * callers must then filter rows themselves. Admin cells marked ‡ (aggregate) never allow a single
 * patient's record.
 */
@Component("access")
public class AccessPolicy {

  private final AccessFacts facts;
  private final ClockPort clock;

  public AccessPolicy(AccessFacts facts, ClockPort clock) {
    this.facts = facts;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- task-named checks

  /** Demographics read (row 1 of the matrix). */
  public boolean canReadPatient(UUID patientId) {
    return can(AccessResource.DEMOGRAPHICS, AccessAction.R, patientId);
  }

  /** Demographics update (patient: own contact details¹; front desk). */
  public boolean canWritePatient(UUID patientId) {
    return can(AccessResource.DEMOGRAPHICS, AccessAction.U, patientId);
  }

  /** Read one lab order; lab techs only while the lab is handling that order. */
  public boolean canReadLabOrder(UUID labOrderId) {
    if (labOrderId == null) {
      return false;
    }
    Optional<AccessFacts.LabOrderRef> order = facts.labOrder(labOrderId);
    if (order.isEmpty()) {
      return false;
    }
    AccessFacts.LabOrderRef ref = order.get();
    Instant now = clock.now();
    return evaluate(
        AccessResource.LAB_ORDERS,
        AccessAction.R,
        ref.patientId(),
        scope -> scope == AccessScope.LINKED_LAB_ORDER && AccessRules.labOrderHandled(ref, now));
  }

  /**
   * Read one prescription. Drafts follow the "Draft prescription" row (doctor only); signed ones the
   * "Signed e-prescription" row (patient, doctor, pharmacist). Pharmacists see signed ones only.
   */
  public boolean canReadPrescription(UUID prescriptionId) {
    if (prescriptionId == null) {
      return false;
    }
    Optional<AccessFacts.PrescriptionRef> rx = facts.prescription(prescriptionId);
    if (rx.isEmpty()) {
      return false;
    }
    AccessFacts.PrescriptionRef ref = rx.get();
    AccessResource resource =
        ref.status() == PrescriptionStatus.DRAFT
            ? AccessResource.DRAFT_PRESCRIPTION
            : AccessResource.SIGNED_PRESCRIPTION;
    return evaluate(
        resource,
        AccessAction.R,
        ref.patientId(),
        scope ->
            scope == AccessScope.LINKED_PRESCRIPTION
                && AccessRules.SIGNED_STATUSES.contains(ref.status()));
  }

  // ---------------------------------------------------------------- generic

  /** SpEL-friendly variant: {@code @access.can('LAB_REPORTS','V',#patientId)}. */
  public boolean can(String resource, String action, UUID patientId) {
    try {
      return can(AccessResource.valueOf(resource), AccessAction.valueOf(action), patientId);
    } catch (IllegalArgumentException | NullPointerException e) {
      return false;
    }
  }

  public boolean can(AccessResource resource, AccessAction action, UUID patientId) {
    return evaluate(resource, action, patientId, null);
  }

  /**
   * True if the best cell the current user holds for this resource is marked minimum-necessary (R*,
   * R†, R³): the caller must return only the reduced data set.
   */
  public boolean minimumNecessary(String resource) {
    AccessResource res;
    try {
      res = AccessResource.valueOf(resource);
    } catch (IllegalArgumentException | NullPointerException e) {
      return true;
    }
    Set<Role> roles = currentRoles();
    boolean any = false;
    for (Role role : roles) {
      Optional<AccessMatrix.Cell> cell = AccessMatrix.cell(res, role);
      if (cell.isPresent()) {
        any = true;
        if (!cell.get().minimumNecessary()) {
          return false;
        }
      }
    }
    return any;
  }

  /** Whether the current user has doctor-level (assigned / ER / break-glass) access to the patient. */
  public boolean isAssigned(UUID patientId) {
    return can(AccessResource.CONSULTATION_NOTES, AccessAction.R, patientId)
        && !currentRoles().contains(Role.ROLE_PATIENT);
  }

  /** ER assignee (or unassigned pool / break-glass) for the current user. */
  public boolean isErAssigned(UUID patientId) {
    return can(AccessResource.CONSULTATION_NOTES, AccessAction.R, patientId)
        && currentRoles().contains(Role.ROLE_ER);
  }

  public boolean canReadVitals(UUID patientId) {
    return can(AccessResource.VITALS, AccessAction.R, patientId);
  }

  public boolean canWriteVitals(UUID patientId) {
    return can(AccessResource.VITALS, AccessAction.C, patientId)
        || can(AccessResource.VITALS, AccessAction.U, patientId);
  }

  public boolean canReadHistory(UUID patientId) {
    return can(AccessResource.HISTORY_ALLERGIES, AccessAction.R, patientId);
  }

  public boolean canWriteHistory(UUID patientId) {
    return can(AccessResource.HISTORY_ALLERGIES, AccessAction.C, patientId)
        || can(AccessResource.HISTORY_ALLERGIES, AccessAction.U, patientId);
  }

  public boolean canReadConsultation(UUID patientId) {
    return can(AccessResource.CONSULTATION_NOTES, AccessAction.R, patientId);
  }

  public boolean canWriteConsultation(UUID patientId) {
    return can(AccessResource.CONSULTATION_NOTES, AccessAction.C, patientId)
        || can(AccessResource.CONSULTATION_NOTES, AccessAction.U, patientId);
  }

  public boolean canSignPrescription(UUID patientId) {
    return can(AccessResource.SIGNED_PRESCRIPTION, AccessAction.S, patientId);
  }

  public boolean canDispense(UUID patientId) {
    return can(AccessResource.SIGNED_PRESCRIPTION, AccessAction.D, patientId)
        || can(AccessResource.DISPENSING, AccessAction.C, patientId);
  }

  public boolean canVerifyLabReport(UUID patientId) {
    return can(AccessResource.LAB_REPORTS, AccessAction.V, patientId);
  }

  public boolean canReadAudit(UUID patientId) {
    return can(AccessResource.AUDIT_LOG, AccessAction.R, patientId);
  }

  public boolean canManageUsers() {
    return can(AccessResource.SETTINGS_USERS, AccessAction.C, null)
        || can(AccessResource.SETTINGS_USERS, AccessAction.U, null);
  }

  // ---------------------------------------------------------------- core

  private boolean evaluate(
      AccessResource resource,
      AccessAction action,
      UUID patientId,
      Predicate<AccessScope> objectLink) {
    if (resource == null || action == null) {
      return false;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return false;
    }
    Set<Role> roles = roles(auth);
    if (roles.isEmpty()) {
      return false;
    }
    Context ctx = new Context(auth.getName());
    for (Role role : roles) {
      Optional<AccessMatrix.Cell> cell = AccessMatrix.cell(resource, role);
      if (cell.isEmpty() || !cell.get().actions().contains(action)) {
        continue;
      }
      if (inScope(cell.get().scope(), resource, patientId, objectLink, ctx)) {
        return true;
      }
    }
    return false;
  }

  private boolean inScope(
      AccessScope scope,
      AccessResource resource,
      UUID patientId,
      Predicate<AccessScope> objectLink,
      Context ctx) {
    if (scope == AccessScope.GLOBAL || !resource.patientScoped()) {
      return true;
    }
    if (patientId == null) {
      // List-level question ("may I open the worklist?"): the role holds the cell.
      return true;
    }
    Instant now = clock.now();
    return switch (scope) {
      case GLOBAL -> true;
      case AGGREGATE -> false;
      case STAFF -> true;
      case OWN -> ctx.userId().flatMap(facts::patientIdOfUser).map(patientId::equals).orElse(false);
      case ASSIGNED ->
          ctx.userId()
              .map(
                  uid ->
                      facts.seenByDoctorToday(uid, patientId, now)
                          || facts.activeReferral(uid, patientId, now)
                          || facts.activeBreakGlass(uid, patientId, now))
              .orElse(false);
      case ER_ASSIGNED ->
          ctx.userId()
              .map(
                  uid ->
                      facts.erAssignedOnActiveAlert(uid, patientId)
                          || facts.activeBreakGlass(uid, patientId, now))
              .orElse(false);
      // Object-level checks (one order / one prescription) use only that object's link, so a lab
      // tech cannot read a cancelled order just because the patient has another open one.
      case LINKED_LAB_ORDER ->
          objectLink != null ? objectLink.test(scope) : facts.openLabOrderForPatient(patientId, now);
      case LINKED_PRESCRIPTION ->
          objectLink != null
              ? objectLink.test(scope)
              : facts.signedPrescriptionForPatient(patientId, now);
    };
  }

  private Set<Role> currentRoles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
      return Set.of();
    }
    return roles(auth);
  }

  public static Set<Role> roles(Authentication auth) {
    EnumSet<Role> roles = EnumSet.noneOf(Role.class);
    for (GrantedAuthority authority : auth.getAuthorities()) {
      try {
        roles.add(Role.valueOf(authority.getAuthority()));
      } catch (IllegalArgumentException ignored) {
        // not a MediFlow role (e.g. FACTOR_* authorities)
      }
    }
    return roles;
  }

  /** Lazily resolves the app_user id once per check. */
  private final class Context {
    private final String username;
    private Optional<UUID> userId;

    Context(String username) {
      this.username = username;
    }

    Optional<UUID> userId() {
      if (userId == null) {
        userId = username == null ? Optional.empty() : facts.userIdByUsername(username);
      }
      return userId;
    }
  }
}
