package com.mediflow.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for {@code @SpringBootTest} integration tests (name them {@code *IT} so failsafe runs
 * them during {@code ./mvnw verify}).
 *
 * <p>One PostgreSQL 16 container is started per JVM and shared by every subclass, so the Spring
 * context is cached across test classes. The {@code test} profile disables Redis, Spring Session,
 * RabbitMQ and mail auto-configuration; a test that needs RabbitMQ should start its own {@code
 * RabbitMQContainer} and re-enable it.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("mediflow")
          .withUsername("mediflow")
          .withPassword("mediflow");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
