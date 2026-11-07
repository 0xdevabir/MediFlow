package com.mediflow.identity.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.identity.access.AccessFacts;
import com.mediflow.identity.access.AccessPolicy;
import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.ConsentType;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ConsentAndReferralShareTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-07T06:00:00Z"));
  private final List<Consent> consentRows = new ArrayList<>();
  private final List<ReferralShare> shareRows = new ArrayList<>();
  private final UUID patientUser = UUID.randomUUID();
  private final UUID patient = UUID.randomUUID();
  private final UUID doctorA = UUID.randomUUID();
  private final UUID doctorB = UUID.randomUUID();
  private final UUID nurse = UUID.randomUUID();
  private final AtomicReference<UUID> actor = new AtomicReference<>(patientUser);
  private final AccessPolicy policy = mock(AccessPolicy.class);
  private ConsentService consents;
  private ReferralShareService shares;

  @BeforeEach
  void setUp() {
    IdentityConsentRepository repo = mock(IdentityConsentRepository.class);
    when(repo.save(any()))
        .thenAnswer(
            i -> {
              Consent c = i.getArgument(0);
              if (c.getId() == null) {
                c.setId(UUID.randomUUID());
                consentRows.add(c);
              }
              return c;
            });
    when(repo.findById(any()))
        .thenAnswer(i -> consentRows.stream().filter(c -> c.getId().equals(i.getArgument(0))).findFirst());
    when(repo.findByPatientIdAndConsentTypeOrderByGrantedAtDesc(any(), any()))
        .thenAnswer(
            i ->
                consentRows.stream()
                    .filter(c -> c.getPatientId().equals(i.getArgument(0)) && c.getConsentType() == i.getArgument(1))
                    .sorted(Comparator.comparing(Consent::getGrantedAt).reversed())
                    .toList());

    CurrentUser current = mock(CurrentUser.class);
    when(current.userId()).thenAnswer(i -> Optional.of(actor.get()));
    when(current.requireUserId()).thenAnswer(i -> actor.get());
    consents = new ConsentService(repo, mock(AuditPort.class), now::get, current);

    ReferralShareRepository shareRepo = mock(ReferralShareRepository.class);
    when(shareRepo.save(any()))
        .thenAnswer(
            i -> {
              ReferralShare s = i.getArgument(0);
              if (s.getId() == null) {
                s.setId(UUID.randomUUID());
                shareRows.add(s);
              }
              return s;
            });
    when(shareRepo.findById(any()))
        .thenAnswer(i -> shareRows.stream().filter(s -> s.getId().equals(i.getArgument(0))).findFirst());

    AccessFacts facts = mock(AccessFacts.class);
    when(facts.patientIdOfUser(any())).thenReturn(Optional.empty());
    when(facts.patientIdOfUser(patientUser)).thenReturn(Optional.of(patient));
    when(facts.userIdByUsername(any())).thenReturn(Optional.empty());
    when(facts.userIdByUsername("doctorb")).thenReturn(Optional.of(doctorB));
    when(facts.userIdByUsername("doctora")).thenReturn(Optional.of(doctorA));
    when(facts.userIdByUsername("nurse")).thenReturn(Optional.of(nurse));
    when(facts.userHasRole(doctorA, Role.ROLE_DOCTOR)).thenReturn(true);
    when(facts.userHasRole(doctorB, Role.ROLE_DOCTOR)).thenReturn(true);
    shares = new ReferralShareService(shareRepo, consents, facts, policy, current, mock(AuditPort.class), now::get);
  }

  @Test
  void latestConsentDecisionWins() {
    assertThat(consents.hasActiveConsent(patient, ConsentType.TERMS)).isFalse();
    Consent terms = consents.recordConsent(patient, patientUser, ConsentType.TERMS, true, "TERMS/2026-11");
    assertThat(consents.hasActiveConsent(patient, ConsentType.TERMS)).isTrue();
    consents.revoke(terms.getId());
    assertThat(consents.hasActiveConsent(patient, ConsentType.TERMS)).isFalse();
    now.set(now.get().plusSeconds(5));
    consents.recordConsent(patient, patientUser, ConsentType.RESEARCH, false, "RESEARCH/v1");
    assertThat(consents.hasActiveConsent(patient, ConsentType.RESEARCH)).isFalse();
    assertThatThrownBy(() -> consents.recordConsent(null, null, ConsentType.TERMS, true, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void patientSharesOwnRecordForLimitedTimeAndRevokes() {
    ReferralShare s = shares.share(patient, "doctorb", Duration.ofDays(3), "second opinion", false);
    assertThat(s.getExpiresAt()).isEqualTo(now.get().plus(Duration.ofDays(3)));
    assertThat(s.activeAt(now.get().plus(Duration.ofDays(2)))).isTrue();
    assertThat(s.activeAt(now.get().plus(Duration.ofDays(3)))).isFalse();
    Consent backing = consentRows.stream().filter(c -> c.getId().equals(s.getConsentId())).findFirst().orElseThrow();
    assertThat(backing.getVersionLabel()).isEqualTo(ConsentService.REFERRAL_SHARE_VERSION);
    assertThat(backing.getConsentType()).isEqualTo(ConsentType.REFERRAL_SHARE);
    // referral consent does not count as the registration data-processing consent
    assertThat(consents.hasActiveConsent(patient, ConsentType.DATA_PROCESSING)).isFalse();
    assertThat(consents.hasActiveConsent(patient, ConsentType.REFERRAL_SHARE)).isTrue();

    shares.revoke(s.getId());
    assertThat(s.activeAt(now.get())).isFalse();
    assertThat(backing.getRevokedAt()).isNotNull();
  }

  @Test
  void validation() {
    assertThatThrownBy(() -> shares.share(patient, "nurse", Duration.ofDays(1), null, false))
        .hasMessage("identity.sharing.grantee.invalid");
    assertThatThrownBy(() -> shares.share(patient, "doctorb", Duration.ofDays(31), null, false))
        .hasMessage("identity.sharing.duration.invalid");
    assertThatThrownBy(() -> shares.share(patient, "doctorb", Duration.ofMinutes(10), null, false))
        .hasMessage("identity.sharing.duration.invalid");
  }

  @Test
  void assignedDoctorNeedsPatientAgreementOthersAreDenied() {
    actor.set(doctorA);
    when(policy.isAssigned(patient)).thenReturn(true);
    assertThatThrownBy(() -> shares.share(patient, "doctorb", Duration.ofDays(1), null, false))
        .hasMessage("identity.sharing.consentRequired");
    assertThatThrownBy(() -> shares.share(patient, "doctora", Duration.ofDays(1), null, true))
        .hasMessage("identity.sharing.grantee.self");
    ReferralShare s = shares.share(patient, "doctorb", Duration.ofDays(1), null, true);
    assertThat(s.getGrantedBy()).isEqualTo(doctorA);

    // doctor B (grantee) cannot revoke or re-share without being assigned
    actor.set(doctorB);
    when(policy.isAssigned(patient)).thenReturn(false);
    assertThatThrownBy(() -> shares.revoke(s.getId())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> shares.share(patient, "doctora", Duration.ofDays(1), null, true))
        .isInstanceOf(AccessDeniedException.class);
  }
}
