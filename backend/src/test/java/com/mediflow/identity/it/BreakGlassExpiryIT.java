package com.mediflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mediflow.identity.access.AccessPolicy;
import com.mediflow.identity.breakglass.BreakGlassService;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Break-glass opens access for exactly 60 minutes, audited and listed for admin review. */
@ActiveProfiles({"test", "dev"})
class BreakGlassExpiryIT extends AbstractIntegrationTest {

  @MockitoBean ClockPort clock;
  @MockitoBean com.mediflow.shared.port.AuditPort audit;
  @Autowired BreakGlassService breakGlass;
  @Autowired PatientAccessPort patientAccess;
  @Autowired AccessPolicy access;
  @Autowired AppUserRepository users;
  @Autowired JdbcTemplate jdbc;

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-03-01T04:00:00Z"));
  private UUID patientId;

  @BeforeEach
  void setUp() {
    when(clock.now()).thenAnswer(i -> now.get());
    patientId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO patient (id, mrn, given_name, family_name) VALUES (?, ?, 'Break', 'Glass')",
        patientId,
        "BG-" + patientId.toString().substring(0, 8));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void accessLastsSixtyMinutes() {
    UUID doctorId = users.findByUsername("doctor").orElseThrow().getId();
    signIn("doctor", "ROLE_DOCTOR");
    assertThat(patientAccess.isAssigned(doctorId, patientId)).isFalse();
    assertThat(access.canReadPatient(patientId)).isFalse();

    breakGlass.activate(patientId, "Unconscious patient, history needed now");
    assertThat(patientAccess.isAssigned(doctorId, patientId)).isTrue();
    assertThat(access.canReadPatient(patientId)).isTrue();
    org.mockito.Mockito.verify(audit)
        .record(
            org.mockito.ArgumentMatchers.eq(doctorId),
            org.mockito.ArgumentMatchers.eq(patientId),
            org.mockito.ArgumentMatchers.eq("BREAK_GLASS_ACTIVATED"),
            org.mockito.ArgumentMatchers.eq("BreakGlassAccess"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyMap());

    now.set(now.get().plus(Duration.ofMinutes(59)));
    assertThat(patientAccess.isAssigned(doctorId, patientId)).isTrue();
    now.set(now.get().plus(Duration.ofMinutes(1)));
    assertThat(patientAccess.isAssigned(doctorId, patientId)).isFalse();
    assertThat(access.canReadPatient(patientId)).isFalse();

    signIn("admin", "ROLE_ADMIN");
    List<BreakGlassService.ReviewItem> review = breakGlass.listForReview();
    assertThat(review).anySatisfy(item -> assertThat(item.toString()).contains("BG-"));
  }

  @Test
  void shortReasonIsRejected() {
    signIn("er", "ROLE_ER");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> breakGlass.activate(patientId, "because"));
  }

  @Test
  void nurseCannotBreakGlass() {
    signIn("nurse", "ROLE_NURSE");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> breakGlass.activate(patientId, "Unconscious patient, history needed now"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  private static void signIn(String username, String role) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(username, "n/a", List.of(new SimpleGrantedAuthority(role))));
  }
}
