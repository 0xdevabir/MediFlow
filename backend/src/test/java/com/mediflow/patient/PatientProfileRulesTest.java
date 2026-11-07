package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.patient.domain.PatientProfile;
import com.mediflow.patient.repo.DemographicChangeRequestRepository;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.repo.PatientProfileRepository;
import com.mediflow.patient.service.OtpIssuer;
import com.mediflow.patient.service.PatientAudit;
import com.mediflow.patient.service.PatientProfileService;
import com.mediflow.patient.service.PatientRuleException;
import com.mediflow.patient.service.QrTokenService;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contact-only update rule: name/DOB are never changed by updateContact. */
class PatientProfileRulesTest {

  PatientLookupRepository patients = mock(PatientLookupRepository.class);
  PatientProfileRepository profiles = mock(PatientProfileRepository.class);
  DemographicChangeRequestRepository changes = mock(DemographicChangeRequestRepository.class);
  OtpIssuer otp = mock(OtpIssuer.class);
  PatientAudit audit = mock(PatientAudit.class);
  QrTokenService qr = mock(QrTokenService.class);
  ClockPort clock = () -> Instant.parse("2026-11-07T06:00:00Z");
  CurrentUser currentUser = mock(CurrentUser.class);

  PatientProfileService service;
  UUID patientId = UUID.randomUUID();
  Patient patient;
  PatientProfile profile;

  @BeforeEach
  void setUp() {
    com.mediflow.shared.encryption.FieldEncryptionKeyHolder.setKey(new byte[32]);
    service =
        new PatientProfileService(
            patients, profiles, changes, otp, audit, qr, clock, currentUser);
    patient = new Patient();
    patient.setId(patientId);
    patient.setGivenName("Karim");
    patient.setFamilyName("Rahman");
    patient.setDateOfBirth(LocalDate.of(1990, 5, 1));
    patient.setPhone("01710000000");
    patient.setEmail("old@example.com");
    patient.setMrn("MF-2026-000010");
    profile = new PatientProfile();
    profile.setPatientId(patientId);
    profile.setPhoneVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"));
    profile.setEmailVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(patients.findById(patientId)).thenReturn(Optional.of(patient));
    when(profiles.findByPatientId(patientId)).thenReturn(Optional.of(profile));
    when(patients.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void updateContactDoesNotTouchNameOrDobAndClearsVerification() {
    service.updateContact(patientId, "01719999999", "new@example.com", "Chittagong");
    assertThat(patient.getGivenName()).isEqualTo("Karim");
    assertThat(patient.getFamilyName()).isEqualTo("Rahman");
    assertThat(patient.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 1));
    assertThat(patient.getPhone()).isEqualTo("01719999999");
    assertThat(patient.getEmail()).isEqualTo("new@example.com");
    assertThat(patient.getAddress()).isEqualTo("Chittagong");
    assertThat(profile.getPhoneVerifiedAt()).isNull();
    assertThat(profile.getEmailVerifiedAt()).isNull();
    verify(otp).issue(patient, com.mediflow.patient.domain.ContactChannel.SMS);
    verify(otp).issue(patient, com.mediflow.patient.domain.ContactChannel.EMAIL);
  }

  @Test
  void emptyDemographicChangeRejected() {
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(() -> service.requestDemographicChange(patientId, "  ", null, null, "n/a"))
        .isInstanceOf(PatientRuleException.class)
        .extracting(ex -> ((PatientRuleException) ex).messageKey())
        .isEqualTo("patient.change.empty");
  }
}
