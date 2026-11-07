package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.patient.domain.ClinicalSource;
import com.mediflow.patient.domain.PatientAllergy;
import com.mediflow.patient.repo.PatientAllergyRepository;
import com.mediflow.patient.repo.PatientHistoryItemRepository;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.service.PatientAudit;
import com.mediflow.patient.service.PatientClinicalHistoryService;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientClinicalHistoryServiceTest {

  PatientLookupRepository patients = mock(PatientLookupRepository.class);
  PatientAllergyRepository allergies = mock(PatientAllergyRepository.class);
  PatientHistoryItemRepository history = mock(PatientHistoryItemRepository.class);
  PatientAudit audit = mock(PatientAudit.class);
  CurrentUser currentUser = mock(CurrentUser.class);
  ClockPort clock = () -> Instant.parse("2026-11-07T06:00:00Z");

  PatientClinicalHistoryService service;
  UUID patientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new PatientClinicalHistoryService(
            patients, allergies, history, audit, currentUser, clock);
    Patient p = new Patient();
    p.setId(patientId);
    when(patients.findById(patientId)).thenReturn(Optional.of(p));
    when(allergies.save(any())).thenAnswer(inv -> {
      PatientAllergy a = inv.getArgument(0);
      a.setId(UUID.randomUUID());
      return a;
    });
    when(currentUser.userId()).thenReturn(Optional.of(UUID.randomUUID()));
  }

  @Test
  void selfReportedAllergyIsUnverified() {
    when(currentUser.hasRole("DOCTOR")).thenReturn(false);
    when(currentUser.hasRole("ER")).thenReturn(false);
    when(currentUser.hasRole("PATIENT")).thenReturn(true);
    PatientAllergy a = service.addAllergy(patientId, "Penicillin", null, "high", null);
    assertThat(a.isVerified()).isFalse();
    assertThat(a.getSource()).isEqualTo(ClinicalSource.SELF_REPORTED);
  }

  @Test
  void doctorConfirmSetsVerified() {
    when(currentUser.hasRole("DOCTOR")).thenReturn(false);
    when(currentUser.hasRole("ER")).thenReturn(false);
    PatientAllergy a = service.addAllergy(patientId, "Aspirin", null, null, null);
    UUID allergyId = a.getId();
    when(allergies.findById(allergyId)).thenReturn(Optional.of(a));
    when(currentUser.hasRole("DOCTOR")).thenReturn(true);
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    PatientAllergy confirmed = service.confirmAllergy(patientId, allergyId);
    assertThat(confirmed.isVerified()).isTrue();
    assertThat(confirmed.getVerifiedAt()).isNotNull();
    assertThat(confirmed.getSource()).isEqualTo(ClinicalSource.CLINICIAN);
  }
}
