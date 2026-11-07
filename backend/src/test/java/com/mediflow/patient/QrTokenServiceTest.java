package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.patient.service.PatientKeys;
import com.mediflow.patient.service.QrTokenService;
import com.mediflow.shared.encryption.FieldEncryptionKeyHolder;
import com.mediflow.shared.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QrTokenServiceTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-07T12:00:00Z"));
  private QrTokenService service;

  @BeforeEach
  void setUp() {
    FieldEncryptionKeyHolder.setKey(new byte[32]);
    ClockPort clock = now::get;
    service = new QrTokenService(clock, Duration.ofMinutes(30));
  }

  @Test
  void issueAndVerifyRoundTrip() {
    UUID id = UUID.randomUUID();
    var issued = service.issue(id, "MF-2026-000001");
    assertThat(service.verify(issued.token()))
        .hasValueSatisfying(
            m -> {
              assertThat(m.patientId()).isEqualTo(id);
              assertThat(m.mrn()).isEqualTo("MF-2026-000001");
            });
  }

  @Test
  void expiredTokenRejected() {
    var issued = service.issue(UUID.randomUUID(), "MF-2026-000002");
    now.set(now.get().plus(Duration.ofMinutes(31)));
    assertThat(service.verify(issued.token())).isEmpty();
  }

  @Test
  void tamperedTokenRejected() {
    var issued = service.issue(UUID.randomUUID(), "MF-2026-000003");
    String tampered = issued.token().substring(0, issued.token().length() - 2) + "aa";
    assertThat(service.verify(tampered)).isEmpty();
    assertThat(PatientKeys.QR_TOKEN).isNotBlank();
  }

  @Test
  void qrDataUriIsPng() {
    assertThat(service.qrDataUri("MF-2026-000001")).startsWith("data:image/png;base64,");
  }
}
