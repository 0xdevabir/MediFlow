package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.patient.service.BlindIndex;
import com.mediflow.patient.service.PatientKeys;
import com.mediflow.shared.encryption.FieldEncryptionKeyHolder;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlindIndexTest {

  @BeforeEach
  void key() {
    FieldEncryptionKeyHolder.setKey(new byte[32]);
  }

  @Test
  void nationalIdNormalisationCollapsesSeparators() {
    assertThat(BlindIndex.normaliseNationalId("1990 1234-567"))
        .isEqualTo(BlindIndex.normaliseNationalId("19901234567"));
  }

  @Test
  void phoneNormalisationMapsBdInternationalToNational() {
    assertThat(BlindIndex.normalisePhone("+880 1712-345678"))
        .isEqualTo(BlindIndex.normalisePhone("01712345678"));
    assertThat(BlindIndex.normalisePhone("8801712345678"))
        .isEqualTo(BlindIndex.normalisePhone("01712345678"));
  }

  @Test
  void identityHashStableAcrossNameCaseAndPhoneFormat() {
    String a =
        BlindIndex.identityHash("Karim", "Rahman", LocalDate.of(1990, 1, 2), "+8801712345678");
    String b =
        BlindIndex.identityHash("karim", "rahman", LocalDate.of(1990, 1, 2), "01712345678");
    assertThat(a).isEqualTo(b).hasSize(64);
  }

  @Test
  void nationalIdHashUsesKeyedHmac() {
    String hash = BlindIndex.nationalIdHash("1990123456789");
    assertThat(hash)
        .isEqualTo(PatientKeys.hmacHex(PatientKeys.BLIND_INDEX, "nid:1990123456789"));
  }
}
