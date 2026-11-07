package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.patient.domain.PatientProfile;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.repo.PatientProfileRepository;
import com.mediflow.patient.service.BlindIndex;
import com.mediflow.patient.service.PatientAudit;
import com.mediflow.patient.service.PatientIdentityService;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.encryption.FieldEncryptionKeyHolder;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientIdentityServiceTest {

  PatientLookupRepository patients = mock(PatientLookupRepository.class);
  PatientProfileRepository profiles = mock(PatientProfileRepository.class);
  PatientAudit audit = mock(PatientAudit.class);
  PatientIdentityService service;

  @BeforeEach
  void setUp() {
    FieldEncryptionKeyHolder.setKey(new byte[32]);
    service = new PatientIdentityService(patients, profiles, audit);
  }

  @Test
  void verifyByMrnAndDob() {
    UUID id = UUID.randomUUID();
    Patient p = new Patient();
    p.setId(id);
    p.setMrn("MF-2026-000001");
    p.setDateOfBirth(LocalDate.of(1990, 1, 2));
    when(patients.findByMrn("MF-2026-000001")).thenReturn(Optional.of(p));
    assertThat(service.verify("MF-2026-000001", LocalDate.of(1990, 1, 2)))
        .hasValueSatisfying(m -> assertThat(m.patientId()).isEqualTo(id));
    assertThat(service.verify("MF-2026-000001", LocalDate.of(1991, 1, 2))).isEmpty();
  }

  @Test
  void verifyByNationalIdAndDob() {
    UUID id = UUID.randomUUID();
    Patient p = new Patient();
    p.setId(id);
    p.setMrn("MF-2026-000002");
    p.setDateOfBirth(LocalDate.of(1988, 3, 4));
    PatientProfile profile = new PatientProfile();
    profile.setPatientId(id);
    String hash = BlindIndex.nationalIdHash("1990123456789");
    when(profiles.findByNationalIdHash(hash)).thenReturn(Optional.of(profile));
    when(patients.findById(id)).thenReturn(Optional.of(p));
    assertThat(service.verify("1990-1234-56789", LocalDate.of(1988, 3, 4))).isPresent();
    when(profiles.findByNationalIdHash(anyString())).thenReturn(Optional.empty());
    assertThat(service.verify("nope", LocalDate.of(1988, 3, 4))).isEmpty();
  }
}
