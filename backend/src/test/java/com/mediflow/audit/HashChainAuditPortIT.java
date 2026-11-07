package com.mediflow.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.audit.service.AuditChainService;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Tamper detection + append-only DB trigger. Needs Docker/Testcontainers — written but not run in
 * this session per coordinator rules.
 */
@ActiveProfiles({"test", "dev"})
class HashChainAuditPortIT extends AbstractIntegrationTest {

  @DynamicPropertySource
  static void disableAuditJobs(DynamicPropertyRegistry registry) {
    registry.add("mediflow.audit.scheduling.enabled", () -> "false");
  }

  @Autowired AuditPort audit;
  @Autowired AuditChainService chain;
  @Autowired JdbcTemplate jdbc;

  @Test
  void realPortReplacesStubAndChains() {
    assertThat(audit.getClass().getSimpleName()).isEqualTo("HashChainAuditPort");
    UUID actor = UUID.randomUUID();
    audit.record(actor, null, "TEST_ACTION", "Test", UUID.randomUUID(), Map.of("purpose", "it"));
    audit.record(actor, null, "TEST_ACTION", "Test", UUID.randomUUID(), Map.of("purpose", "it2"));

    var check = chain.verifyNow(null);
    assertThat(check.ok()).isTrue();
    assertThat(check.rowsChecked()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void databaseRejectsUpdateAndDelete() {
    UUID id = UUID.randomUUID();
    audit.record(
        UUID.randomUUID(), null, "IMMUTABLE", "Test", id, Map.of("purpose", "lock"));
    UUID rowId =
        jdbc.queryForObject(
            "SELECT id FROM audit_event WHERE action = 'IMMUTABLE' ORDER BY seq DESC LIMIT 1",
            UUID.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE audit_event SET action = 'X' WHERE id = ?", rowId))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE id = ?", rowId))
        .hasMessageContaining("append-only");
  }

  @Test
  void contentTamperIsDetectedByVerifier() {
    audit.record(
        UUID.randomUUID(),
        null,
        "TAMPER_TARGET",
        "Test",
        UUID.randomUUID(),
        Map.of("purpose", "before"));
    // Bypass the trigger by temporarily dropping it (test-only). If drop is forbidden, skip.
    jdbc.execute("ALTER TABLE audit_event DISABLE TRIGGER trg_audit_event_no_update");
    try {
      jdbc.update(
          "UPDATE audit_event SET action = 'TAMPERED' WHERE action = 'TAMPER_TARGET'");
    } finally {
      jdbc.execute("ALTER TABLE audit_event ENABLE TRIGGER trg_audit_event_no_update");
    }
    var check = chain.verifyNow(null);
    assertThat(check.ok()).isFalse();
    assertThat(check.firstBrokenSeq()).isNotNull();
  }
}
