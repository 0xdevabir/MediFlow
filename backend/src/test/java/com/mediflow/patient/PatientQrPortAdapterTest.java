package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.service.PatientQrPortAdapter;
import com.mediflow.patient.service.PatientRuleException;
import com.mediflow.patient.service.QrTokenService;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.encryption.FieldEncryptionKeyHolder;
import com.mediflow.shared.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientQrPortAdapterTest {

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-11-07T12:00:00Z"));
  private PatientLookupRepository patients;
  private PatientQrPortAdapter port;

  @BeforeEach
  void setUp() {
    FieldEncryptionKeyHolder.setKey(new byte[32]);
    ClockPort clock = now::get;
    QrTokenService qr = new QrTokenService(clock, Duration.ofMinutes(30));
    patients = mock(PatientLookupRepository.class);
    port = new PatientQrPortAdapter(qr, patients);
  }

  @Test
  void issueAndResolveRoundTrip() {
    UUID id = UUID.randomUUID();
    Patient patient = newPatient(id, "MF-2026-000010");
    when(patients.findById(id)).thenReturn(Optional.of(patient));

    String token = port.issueToken(id, Duration.ofMinutes(15));
    assertThat(port.resolvePatientId(token)).contains(id);
  }

  @Test
  void customTtlExpires() {
    UUID id = UUID.randomUUID();
    when(patients.findById(id)).thenReturn(Optional.of(newPatient(id, "MF-2026-000011")));

    String token = port.issueToken(id, Duration.ofMinutes(5));
    now.set(now.get().plus(Duration.ofMinutes(6)));
    assertThat(port.resolvePatientId(token)).isEmpty();
  }

  @Test
  void unknownPatientRejected() {
    UUID id = UUID.randomUUID();
    when(patients.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> port.issueToken(id, Duration.ofMinutes(10)))
        .isInstanceOf(PatientRuleException.class);
  }

  @Test
  void malformedTokenEmpty() {
    assertThat(port.resolvePatientId("not-a-token")).isEmpty();
    assertThat(port.resolvePatientId(null)).isEmpty();
  }

  private static Patient newPatient(UUID id, String mrn) {
    Patient p = new Patient();
    p.setId(id);
    p.setMrn(mrn);
    p.setGivenName("Test");
    p.setFamilyName("Patient");
    p.setDateOfBirth(LocalDate.of(1990, 1, 1));
    return p;
  }
}
