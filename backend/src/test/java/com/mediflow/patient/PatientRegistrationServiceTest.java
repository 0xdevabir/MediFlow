package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.patient.domain.ContactChannel;
import com.mediflow.patient.domain.PatientProfile;
import com.mediflow.patient.domain.RegistrationSource;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.repo.PatientProfileRepository;
import com.mediflow.patient.service.DuplicatePatientException;
import com.mediflow.patient.service.MrnGenerator;
import com.mediflow.patient.service.NewPatient;
import com.mediflow.patient.service.OtpIssuer;
import com.mediflow.patient.service.PatientAudit;
import com.mediflow.patient.service.PatientConsentGateway;
import com.mediflow.patient.service.PatientRegistrationService;
import com.mediflow.patient.service.PatientRuleException;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class PatientRegistrationServiceTest {

  PatientLookupRepository patients = mock(PatientLookupRepository.class);
  PatientProfileRepository profiles = mock(PatientProfileRepository.class);
  AppUserRepository users = mock(AppUserRepository.class);
  MrnGenerator mrns = mock(MrnGenerator.class);
  PatientConsentGateway consent = mock(PatientConsentGateway.class);
  OtpIssuer otp = mock(OtpIssuer.class);
  PatientAudit audit = mock(PatientAudit.class);
  PasswordEncoder passwords = mock(PasswordEncoder.class);
  ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  ClockPort clock = () -> Instant.parse("2026-11-07T06:00:00Z");
  CurrentUser currentUser = mock(CurrentUser.class);

  PatientRegistrationService service;

  @BeforeEach
  void setUp() {
    com.mediflow.shared.encryption.FieldEncryptionKeyHolder.setKey(new byte[32]);
    service =
        new PatientRegistrationService(
            patients, profiles, users, mrns, consent, otp, audit, passwords, events, clock, currentUser);
    when(mrns.next()).thenReturn("MF-2026-000001");
    when(passwords.encode(anyString())).thenReturn("hash");
    when(users.save(any(AppUser.class)))
        .thenAnswer(
            inv -> {
              AppUser u = inv.getArgument(0);
              u.setId(UUID.randomUUID());
              return u;
            });
    when(patients.save(any(Patient.class)))
        .thenAnswer(
            inv -> {
              Patient p = inv.getArgument(0);
              if (p.getId() == null) {
                p.setId(UUID.randomUUID());
              }
              return p;
            });
    when(profiles.save(any(PatientProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    when(profiles.findByNationalIdHash(anyString())).thenReturn(Optional.empty());
    when(profiles.findByIdentityHash(anyString())).thenReturn(List.of());
    when(users.findByUsername(anyString())).thenReturn(Optional.empty());
  }

  private NewPatient demo() {
    return new NewPatient(
        "Karim", "Rahman", LocalDate.of(1990, 5, 1), "MALE", "01712345678", "k@example.com", "1990123456789", "Dhaka");
  }

  @Test
  void selfRegistrationCreatesUserPublishesEventAndAudits() {
    Patient patient = service.registerSelf(demo(), "karim", "password-long", true);
    assertThat(patient.getMrn()).isEqualTo("MF-2026-000001");
    assertThat(patient.getUserId()).isNotNull();
    verify(consent).recordRegistrationConsent(eq(patient.getId()), any(), eq("REGISTRATION/v1"));
    verify(otp, atLeastOnce()).issue(eq(patient), eq(ContactChannel.SMS));
    ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue()).isInstanceOf(DomainEvents.PatientRegistered.class);
    verify(audit)
        .recordAs(any(), eq(patient.getId()), eq("PATIENT_REGISTERED"), eq("Patient"), eq(patient.getId()), anyMap());
  }

  @Test
  void selfRegistrationRequiresConsent() {
    assertThatThrownBy(() -> service.registerSelf(demo(), "karim", "password-long", false))
        .isInstanceOf(PatientRuleException.class)
        .extracting(ex -> ((PatientRuleException) ex).messageKey())
        .isEqualTo("patient.register.consentRequired");
  }

  @Test
  void duplicateNationalIdRejected() {
    PatientProfile existing = new PatientProfile();
    existing.setPatientId(UUID.randomUUID());
    when(profiles.findByNationalIdHash(anyString())).thenReturn(Optional.of(existing));
    assertThatThrownBy(() -> service.registerSelf(demo(), "karim", "password-long", true))
        .isInstanceOf(DuplicatePatientException.class)
        .extracting(ex -> ((DuplicatePatientException) ex).kind())
        .isEqualTo(DuplicatePatientException.Kind.NATIONAL_ID);
  }

  @Test
  void duplicateNameDobPhoneRejected() {
    PatientProfile existing = new PatientProfile();
    existing.setPatientId(UUID.randomUUID());
    when(profiles.findByIdentityHash(anyString())).thenReturn(List.of(existing));
    NewPatient withoutNid =
        new NewPatient("Karim", "Rahman", LocalDate.of(1990, 5, 1), "MALE", "01712345678", null, null, null);
    assertThatThrownBy(() -> service.registerSelf(withoutNid, "karim", "password-long", true))
        .isInstanceOf(DuplicatePatientException.class)
        .extracting(ex -> ((DuplicatePatientException) ex).kind())
        .isEqualTo(DuplicatePatientException.Kind.NAME_DOB_PHONE);
  }

  @Test
  void walkInDoesNotCreatePortalUser() {
    UUID actor = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(actor);
    Patient patient = service.registerWalkIn(demo(), true);
    assertThat(patient.getUserId()).isNull();
    verify(events).publishEvent(any(DomainEvents.PatientRegistered.class));
    ArgumentCaptor<PatientProfile> profile = ArgumentCaptor.forClass(PatientProfile.class);
    verify(profiles).save(profile.capture());
    assertThat(profile.getValue().getRegistrationSource()).isEqualTo(RegistrationSource.FRONT_DESK);
    assertThat(profile.getValue().getRegisteredBy()).isEqualTo(actor);
    verify(audit).record(eq(patient.getId()), eq("PATIENT_REGISTERED"), eq("Patient"), eq(patient.getId()), anyMap());
    verify(audit, org.mockito.Mockito.never())
        .recordAs(any(), any(), anyString(), anyString(), any(), anyMap());
  }
}
