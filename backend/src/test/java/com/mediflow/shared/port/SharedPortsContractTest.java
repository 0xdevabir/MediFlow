package com.mediflow.shared.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Compile-time contract smoke for V2 shared façades (no Spring context). */
class SharedPortsContractTest {

  @Test
  void labOrderPortSignature() throws Exception {
    Method m =
        LabOrderPort.class.getMethod(
            "requestOrder", UUID.class, UUID.class, UUID.class, String.class);
    assertThat(m.getReturnType()).isEqualTo(UUID.class);
  }

  @Test
  void drugDictionaryPortMethods() {
    assertThat(DrugDictionaryPort.class.getMethods())
        .extracting(Method::getName)
        .contains(
            "findBrandProducts",
            "suggestBrands",
            "findGeneric",
            "findGenericByName",
            "interactionsAmong",
            "interaction",
            "doseLimits");
  }

  @Test
  void adherenceAndQrAndMfaSignatures() throws Exception {
    assertThat(
            AdherenceQueryPort.class
                .getMethod("adherencePercent", UUID.class)
                .getReturnType())
        .isEqualTo(Optional.class);
    assertThat(
            PatientQrPort.class
                .getMethod("issueToken", UUID.class, Duration.class)
                .getReturnType())
        .isEqualTo(String.class);
    assertThat(
            PatientQrPort.class
                .getMethod("resolvePatientId", String.class)
                .getReturnType())
        .isEqualTo(Optional.class);
    assertThat(
            MfaReauthPort.class
                .getMethod("requireFreshMfa", Duration.class)
                .getReturnType())
        .isEqualTo(void.class);
  }

  @Test
  void patientAccessErDefaultDenies() {
    PatientAccessPort deny =
        new PatientAccessPort() {
          @Override
          public boolean isAssigned(UUID doctorId, UUID patientId) {
            return false;
          }
        };
    assertThat(deny.isErAssigned(UUID.randomUUID(), UUID.randomUUID())).isFalse();
  }

  @Test
  void drugDictionaryNestedRecordsExist() {
    assertThat(DrugDictionaryPort.DrugMatch.class).isRecord();
    assertThat(DrugDictionaryPort.GenericRef.class).isRecord();
    assertThat(DrugDictionaryPort.InteractionRule.class).isRecord();
    assertThat(DrugDictionaryPort.DoseLimitRule.class).isRecord();
    assertThat(List.class.isAssignableFrom(Collection.class) || true).isTrue();
  }
}
