package com.mediflow.pharmacy.dispensing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.PatientQrPort;
import com.mediflow.shared.repo.PatientRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DefaultPatientIdentityVerifierTest {

  private PatientRepository patients;
  private ObjectProvider<PatientQrPort> qrPort;
  private DefaultPatientIdentityVerifier verifier;
  private UUID patientId;
  private Patient patient;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    patients = mock(PatientRepository.class);
    qrPort = mock(ObjectProvider.class);
    when(qrPort.getIfAvailable()).thenReturn(null);
    verifier = new DefaultPatientIdentityVerifier(patients, qrPort);
    patientId = UUID.randomUUID();
    patient = new Patient();
    patient.setId(patientId);
    patient.setMrn("MRN-100");
    patient.setNationalId("1990123456789");
    patient.setPhone("+8801712345678");
    patient.setGivenName("Amina");
    patient.setFamilyName("Rahman");
    when(patients.findById(patientId)).thenReturn(Optional.of(patient));
  }

  @Test
  void qrNeverAcceptedWithoutPort() {
    assertTrue(verifier.verifyQr("anything").isEmpty());
  }

  @Test
  void qrAcceptedWhenPortResolvesPatient() {
    PatientQrPort port = mock(PatientQrPort.class);
    when(qrPort.getIfAvailable()).thenReturn(port);
    when(port.resolvePatientId("tok-1")).thenReturn(Optional.of(patientId));
    var verified = verifier.verifyQr("tok-1");
    assertTrue(verified.isPresent());
    assertEquals(patientId, verified.get().patientId());
    assertEquals(PatientIdentityPort.Method.QR, verified.get().method());
  }

  @Test
  void twoMatchingIdentifiersPass() {
    assertTrue(
        verifier
            .verifyTwoIdentifiers(patientId, "MRN-100", "1990123456789")
            .isPresent());
  }

  @Test
  void sameIdentifierTwiceFails() {
    assertTrue(verifier.verifyTwoIdentifiers(patientId, "MRN-100", "mrn-100").isEmpty());
  }

  @Test
  void wrongIdentifierFails() {
    assertTrue(verifier.verifyTwoIdentifiers(patientId, "MRN-100", "nope").isEmpty());
  }

  @Test
  void blankFails() {
    assertTrue(verifier.verifyTwoIdentifiers(patientId, "MRN-100", " ").isEmpty());
  }
}
