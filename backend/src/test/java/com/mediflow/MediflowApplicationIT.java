package com.mediflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class MediflowApplicationIT extends AbstractIntegrationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  void contextLoadsAndFlywayMigrated() {
    Integer applied =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success",
            Integer.class);
    assertThat(applied).isEqualTo(1);

    Integer tables =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name IN ('app_user','patient','audit_event','prescription')
            """,
            Integer.class);
    assertThat(tables).isEqualTo(4);
  }

  @Test
  void auditEventUpdateIsRejected() {
    UUID id = insertAuditEvent("TEST_UPD");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE audit_event SET action = 'TAMPER' WHERE id = ?", id))
        .hasMessageContaining("append-only");
  }

  @Test
  void auditEventDeleteIsRejected() {
    UUID id = insertAuditEvent("TEST_DEL");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE id = ?", id))
        .hasMessageContaining("append-only");
  }

  @Test
  void auditEventTruncateIsRejected() {
    insertAuditEvent("TEST_TRUNC");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_event"))
        .hasMessageContaining("append-only");
  }

  private UUID insertAuditEvent(String action) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO audit_event (id, occurred_at, action, hash, created_at) VALUES (?, now(), ?, 'h', now())",
        id,
        action);
    return id;
  }
}
