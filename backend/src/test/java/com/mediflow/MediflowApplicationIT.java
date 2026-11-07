package com.mediflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MediflowApplicationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("mediflow")
          .withUsername("mediflow")
          .withPassword("mediflow");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  void contextLoadsAndFlywayMigrated() {
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
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO audit_event (id, occurred_at, action, hash, created_at)
        VALUES (?, now(), 'TEST', 'abc', now())
        """,
        id);

    assertThatThrownBy(
            () -> jdbc.update("UPDATE audit_event SET action = 'TAMPER' WHERE id = ?", id))
        .hasMessageContaining("append-only");
  }

  @Test
  void auditEventDeleteIsRejected() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO audit_event (id, occurred_at, action, hash, created_at)
        VALUES (?, now(), 'TEST_DEL', 'def', now())
        """,
        id);

    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE id = ?", id))
        .hasMessageContaining("append-only");
  }
}
