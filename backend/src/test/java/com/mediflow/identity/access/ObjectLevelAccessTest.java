package com.mediflow.identity.access;

import static com.mediflow.identity.access.AccessMatrixPolicyTest.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.shared.domain.LabOrderStatus;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.context.SecurityContextHolder;

/** Minimum-necessary object checks and the assigned-patient port. */
class ObjectLevelAccessTest {

  private static final Instant NOW = Instant.parse("2026-11-07T06:00:00Z");
  private final UUID user = UUID.randomUUID();
  private final UUID patient = UUID.randomUUID();
  private final UUID order = UUID.randomUUID();
  private final UUID rx = UUID.randomUUID();
  private AccessFacts facts;
  private AccessPolicy policy;
  private final ClockPort clock = () -> NOW;

  @BeforeEach
  void setUp() {
    facts = mock(AccessFacts.class);
    when(facts.userIdByUsername(any())).thenReturn(Optional.of(user));
    when(facts.patientIdOfUser(any())).thenReturn(Optional.empty());
    policy = new AccessPolicy(facts, clock);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({
    "ORDERED,0,true",
    "SAMPLE_COLLECTED,0,true",
    "IN_LAB,0,true",
    "RESULTED,0,true",
    "VERIFIED,1,true",
    "VERIFIED,8,false",
    "CANCELLED,0,false"
  })
  void labTechReadsOnlyOrdersTheLabIsHandling(LabOrderStatus status, int daysAgo, boolean allowed) {
    signIn(Role.ROLE_LAB_TECH);
    when(facts.labOrder(order))
        .thenReturn(
            Optional.of(
                new AccessFacts.LabOrderRef(
                    order, patient, status, NOW.minus(Duration.ofDays(daysAgo)))));
    // Even if the patient has another open order, this specific order decides.
    when(facts.openLabOrderForPatient(any(), any())).thenReturn(true);
    assertThat(policy.canReadLabOrder(order)).isEqualTo(allowed);
  }

  @Test
  void labOrderReadByRole() {
    when(facts.labOrder(order))
        .thenReturn(
            Optional.of(new AccessFacts.LabOrderRef(order, patient, LabOrderStatus.ORDERED, NOW)));
    signIn(Role.ROLE_PHARMACIST);
    assertThat(policy.canReadLabOrder(order)).isFalse();
    signIn(Role.ROLE_FRONT_DESK); // C⁶ only, no read
    assertThat(policy.canReadLabOrder(order)).isFalse();
    signIn(Role.ROLE_NURSE);
    assertThat(policy.canReadLabOrder(order)).isTrue();
    signIn(Role.ROLE_DOCTOR);
    assertThat(policy.canReadLabOrder(order)).isFalse();
    when(facts.seenByDoctorToday(user, patient, NOW)).thenReturn(true);
    assertThat(policy.canReadLabOrder(order)).isTrue();
    signIn(Role.ROLE_PATIENT);
    assertThat(policy.canReadLabOrder(order)).isFalse();
    when(facts.patientIdOfUser(user)).thenReturn(Optional.of(patient));
    assertThat(policy.canReadLabOrder(order)).isTrue();
    assertThat(policy.canReadLabOrder(UUID.randomUUID())).isFalse(); // unknown order
    assertThat(policy.canReadLabOrder(null)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "ROLE_PHARMACIST,DRAFT,false",
    "ROLE_PHARMACIST,SIGNED,true",
    "ROLE_PHARMACIST,AMENDED,true",
    "ROLE_PHARMACIST,ENTERED_IN_ERROR,true",
    "ROLE_PATIENT,DRAFT,false",
    "ROLE_PATIENT,SIGNED,true",
    "ROLE_DOCTOR,DRAFT,true",
    "ROLE_DOCTOR,SIGNED,true",
    "ROLE_NURSE,SIGNED,false",
    "ROLE_LAB_TECH,SIGNED,false",
    "ROLE_ADMIN,SIGNED,false",
    "ROLE_FRONT_DESK,SIGNED,false"
  })
  void prescriptionReadFollowsDraftAndSignedRows(
      Role role, PrescriptionStatus status, boolean allowed) {
    signIn(role);
    when(facts.prescription(rx))
        .thenReturn(Optional.of(new AccessFacts.PrescriptionRef(rx, patient, status, NOW)));
    when(facts.patientIdOfUser(user)).thenReturn(Optional.of(patient));
    when(facts.seenByDoctorToday(user, patient, NOW)).thenReturn(true);
    assertThat(policy.canReadPrescription(rx)).isEqualTo(allowed);
  }

  @Test
  void doctorAccessViaBreakGlassOrReferral() {
    signIn(Role.ROLE_DOCTOR);
    assertThat(policy.canReadPatient(patient)).isFalse();
    when(facts.activeBreakGlass(user, patient, NOW)).thenReturn(true);
    assertThat(policy.canReadPatient(patient)).isTrue();
    assertThat(policy.isAssigned(patient)).isTrue();
    when(facts.activeBreakGlass(user, patient, NOW)).thenReturn(false);
    when(facts.activeReferral(user, patient, NOW)).thenReturn(true);
    assertThat(policy.can("CONSULTATION_NOTES", "U", patient)).isTrue();
    assertThat(policy.canWritePatient(patient)).isFalse(); // doctor demographics read-only
  }

  @Test
  void erNeedsActiveRedAlert() {
    signIn(Role.ROLE_ER);
    assertThat(policy.can("VITALS", "R", patient)).isFalse();
    when(facts.activeRedAlert(patient)).thenReturn(true);
    assertThat(policy.can("VITALS", "R", patient)).isTrue();
    assertThat(policy.can("VITALS", "R", UUID.randomUUID())).isFalse();
  }

  @Test
  void patientPortMapsPractitionerAndAppliesRules() {
    IdentityPatientAccessPort port = new IdentityPatientAccessPort(facts, clock);
    UUID practitioner = UUID.randomUUID();
    when(facts.userIdOfPractitioner(practitioner)).thenReturn(Optional.of(user));
    when(facts.userIdOfPractitioner(user)).thenReturn(Optional.empty());

    assertThat(port.isAssigned(user, patient)).isFalse();
    assertThat(port.isAssigned(null, patient)).isFalse();

    when(facts.seenByDoctorToday(eq(user), eq(patient), any())).thenReturn(true);
    assertThat(port.isAssigned(user, patient)).isTrue();
    assertThat(port.isAssigned(practitioner, patient)).isTrue();

    UUID er = UUID.randomUUID();
    when(facts.userIdOfPractitioner(er)).thenReturn(Optional.empty());
    when(facts.userHasRole(er, Role.ROLE_ER)).thenReturn(true);
    assertThat(port.isAssigned(er, patient)).isFalse();
    when(facts.activeRedAlert(patient)).thenReturn(true);
    assertThat(port.isAssigned(er, patient)).isTrue();
    assertThat(port.isErAssigned(er, patient)).isTrue();
  }
}
