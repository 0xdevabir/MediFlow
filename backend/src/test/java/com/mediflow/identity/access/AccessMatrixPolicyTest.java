package com.mediflow.identity.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.ClockPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Every cell of the R&D v1.3 §12 access matrix, for the {@code @access} bean. The expected table is
 * transcribed independently of {@link AccessMatrix} (literal copy of the PDF table, plus the ER
 * footnote "doctor-level access limited to the Red-Alert patient" and CLINICAL_APPROVER = no cells).
 *
 * <p>Each (resource, role, action) cell is checked three ways: a patient inside the role's scope
 * (own record / assigned / linked), a patient outside it, and list level (no patient).
 */
class AccessMatrixPolicyTest {

  // Columns: PATIENT DOCTOR LAB_TECH PHARMACIST NURSE FRONT_DESK ADMIN ER CLINICAL_APPROVER
  private static final Role[] COLUMNS = {
    Role.ROLE_PATIENT,
    Role.ROLE_DOCTOR,
    Role.ROLE_LAB_TECH,
    Role.ROLE_PHARMACIST,
    Role.ROLE_NURSE,
    Role.ROLE_FRONT_DESK,
    Role.ROLE_ADMIN,
    Role.ROLE_ER,
    Role.ROLE_CLINICAL_APPROVER
  };

  // ‡ = aggregated data only (allowed at list level, never for one patient).
  private static final String TABLE =
      """
      DEMOGRAPHICS        | RU | R   | R    | R   | R  | RCU | -   | R   | -
      HISTORY_ALLERGIES   | RC | RCU | -    | R   | -  | -   | -   | RCU | -
      VITALS              | RC | RCU | -    | -   | RC | -   | -   | RCU | -
      APPOINTMENTS        | RC | RU  | -    | -   | RU | RCU | R‡  | RU  | -
      DIRECTORY           | R  | R   | R    | R   | R  | R   | CU  | R   | -
      CONSULTATION_NOTES  | R  | RCU | -    | -   | -  | -   | -   | RCU | -
      DRAFT_PRESCRIPTION  | -  | RCU | -    | -   | -  | -   | -   | RCU | -
      SIGNED_PRESCRIPTION | R  | RS  | -    | RD  | -  | -   | -   | RS  | -
      LAB_ORDERS          | RC | RC  | RU   | -   | R  | C   | -   | RC  | -
      LAB_REPORTS         | R  | R   | RCUV | -   | -  | -   | -   | R   | -
      DISPENSING          | R  | R   | -    | RCU | -  | -   | -   | R   | -
      AI_FLAGS            | -  | RO  | -    | R   | -  | -   | R‡  | RO  | -
      AUDIT_LOG           | R  | -   | -    | -   | -  | -   | R   | -   | -
      SETTINGS_USERS      | -  | -   | -    | -   | -  | -   | CU  | -   | -
      """;

  private static final UUID USER = UUID.randomUUID();
  private static final UUID PATIENT = UUID.randomUUID();
  private static final UUID OTHER_PATIENT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-11-07T06:00:00Z");

  private static Map<AccessResource, Map<Role, String>> expected() {
    Map<AccessResource, Map<Role, String>> m = new EnumMap<>(AccessResource.class);
    for (String line : TABLE.strip().split("\n")) {
      String[] parts = line.split("\\|");
      AccessResource res = AccessResource.valueOf(parts[0].trim());
      Map<Role, String> row = new EnumMap<>(Role.class);
      for (int i = 0; i < COLUMNS.length; i++) {
        row.put(COLUMNS[i], parts[i + 1].trim());
      }
      m.put(res, row);
    }
    return m;
  }

  static Stream<Arguments> everyCell() {
    Map<AccessResource, Map<Role, String>> table = expected();
    assertThat(table).hasSize(AccessResource.values().length);
    List<Arguments> args = new ArrayList<>();
    for (AccessResource res : AccessResource.values()) {
      for (Role role : COLUMNS) {
        for (AccessAction action : AccessAction.values()) {
          args.add(Arguments.of(res, role, action, table.get(res).get(role)));
        }
      }
    }
    return args.stream();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest(name = "{0} {1} {2} = {3}")
  @MethodSource("everyCell")
  void everyCellOfTheAccessMatrix(AccessResource res, Role role, AccessAction action, String cell) {
    boolean granted = cell.contains(action.name());
    boolean aggregateOnly = cell.contains("‡");
    boolean staffWide = role == Role.ROLE_NURSE || role == Role.ROLE_FRONT_DESK;
    boolean globalForRole =
        !res.patientScoped() || (role == Role.ROLE_ADMIN && res == AccessResource.AUDIT_LOG);

    signIn(role);

    AccessPolicy inScope = new AccessPolicy(facts(true), clock());
    assertThat(inScope.can(res, action, PATIENT))
        .as("in scope")
        .isEqualTo(granted && !aggregateOnly);
    assertThat(inScope.can(res.name(), action.name(), PATIENT)).isEqualTo(granted && !aggregateOnly);

    AccessPolicy outOfScope = new AccessPolicy(facts(false), clock());
    assertThat(outOfScope.can(res, action, OTHER_PATIENT))
        .as("out of scope")
        .isEqualTo(granted && !aggregateOnly && (staffWide || globalForRole));

    assertThat(outOfScope.can(res, action, null)).as("list level").isEqualTo(granted);
  }

  @Test
  void matrixClassAgreesWithTranscribedTable() {
    Map<AccessResource, Map<Role, String>> table = expected();
    for (AccessResource res : AccessResource.values()) {
      for (Role role : COLUMNS) {
        for (AccessAction action : AccessAction.values()) {
          assertThat(AccessMatrix.grants(res, role, action))
              .as("%s %s %s", res, role, action)
              .isEqualTo(table.get(res).get(role).contains(action.name()));
        }
      }
    }
  }

  @Test
  void anonymousAndUnknownAreDenied() {
    AccessPolicy policy = new AccessPolicy(facts(true), clock());
    assertThat(policy.canReadPatient(PATIENT)).isFalse();
    signIn(Role.ROLE_DOCTOR);
    assertThat(policy.can("NOPE", "R", PATIENT)).isFalse();
    assertThat(policy.can("VITALS", "X", PATIENT)).isFalse();
    assertThat(policy.can((String) null, null, PATIENT)).isFalse();
  }

  @Test
  void multiRoleUserGetsUnionOfCells() {
    signIn(Role.ROLE_ADMIN, Role.ROLE_NURSE);
    AccessPolicy policy = new AccessPolicy(facts(false), clock());
    assertThat(policy.can(AccessResource.VITALS, AccessAction.C, OTHER_PATIENT)).isTrue();
    assertThat(policy.can(AccessResource.SETTINGS_USERS, AccessAction.U, null)).isTrue();
  }

  @Test
  void minimumNecessaryMarkers() {
    AccessPolicy policy = new AccessPolicy(facts(true), clock());
    signIn(Role.ROLE_LAB_TECH);
    assertThat(policy.minimumNecessary("DEMOGRAPHICS")).isTrue();
    signIn(Role.ROLE_PHARMACIST);
    assertThat(policy.minimumNecessary("HISTORY_ALLERGIES")).isTrue();
    signIn(Role.ROLE_FRONT_DESK);
    assertThat(policy.minimumNecessary("DEMOGRAPHICS")).isFalse();
    signIn(Role.ROLE_DOCTOR, Role.ROLE_NURSE);
    assertThat(policy.minimumNecessary("DEMOGRAPHICS")).isFalse();
  }

  // ------------------------------------------------------------------ helpers

  static void signIn(Role... roles) {
    String[] names = Stream.of(roles).map(Role::name).toArray(String[]::new);
    TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pw", names);
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  static ClockPort clock() {
    return () -> NOW;
  }

  /** All scope facts true (in scope) or false (out of scope). */
  static AccessFacts facts(boolean inScope) {
    AccessFacts f = mock(AccessFacts.class);
    when(f.userIdByUsername(any())).thenReturn(Optional.of(USER));
    when(f.patientIdOfUser(USER)).thenReturn(Optional.of(inScope ? PATIENT : UUID.randomUUID()));
    when(f.seenByDoctorToday(any(), any(), any())).thenReturn(inScope);
    when(f.activeReferral(any(), any(), any())).thenReturn(false);
    when(f.activeBreakGlass(any(), any(), any())).thenReturn(false);
    when(f.activeRedAlert(any())).thenReturn(inScope);
    when(f.openLabOrderForPatient(any(), any())).thenReturn(inScope);
    when(f.signedPrescriptionForPatient(any(), any())).thenReturn(inScope);
    return f;
  }
}
