package com.mediflow.identity.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.identity.login.LoginAttemptService;
import com.mediflow.identity.mfa.MfaService;
import com.mediflow.identity.password.PasswordHasher;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.shared.repo.PractitionerRepository;
import com.mediflow.shared.web.CurrentUser;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaffUserServiceTest {

  private final Map<UUID, AppUser> users = new HashMap<>();
  private final Map<UUID, Practitioner> practitioners = new HashMap<>();
  private final UUID adminId = UUID.randomUUID();
  private final PasswordHasher hasher = new PasswordHasher(false);
  private final MfaService mfa = mock(MfaService.class);
  private StaffUserService service;

  @BeforeEach
  void setUp() {
    AppUserRepository userRepo = mock(AppUserRepository.class);
    when(userRepo.save(any()))
        .thenAnswer(
            i -> {
              AppUser u = i.getArgument(0);
              if (u.getId() == null) {
                u.setId(UUID.randomUUID());
              }
              users.put(u.getId(), u);
              return u;
            });
    when(userRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(users.get(i.<UUID>getArgument(0))));
    when(userRepo.findByUsername(any()))
        .thenAnswer(i -> users.values().stream().filter(u -> u.getUsername().equals(i.getArgument(0))).findFirst());
    PractitionerRepository prRepo = mock(PractitionerRepository.class);
    when(prRepo.save(any()))
        .thenAnswer(
            i -> {
              Practitioner p = i.getArgument(0);
              if (p.getId() == null) {
                p.setId(UUID.randomUUID());
              }
              practitioners.put(p.getId(), p);
              return p;
            });
    when(prRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(practitioners.get(i.<UUID>getArgument(0))));
    CurrentUser current = mock(CurrentUser.class);
    when(current.userId()).thenReturn(Optional.of(adminId));
    service =
        new StaffUserService(
            userRepo, prRepo, hasher, mfa, mock(LoginAttemptService.class), mock(AuditPort.class), current);
  }

  private StaffUserService.NewStaffUser doctor(String username, String pw) {
    return new StaffUserService.NewStaffUser(
        username, "Dr Rahman", pw, Set.of(Role.ROLE_DOCTOR), "dr@example.org", null, "bn");
  }

  @Test
  void createsHashedEncryptedStaffUser() {
    AppUser u = service.createUser(doctor("Dr.Rahman", "Strong-pass-2026"));
    assertThat(u.getUsername()).isEqualTo("dr.rahman");
    assertThat(hasher.matches("Strong-pass-2026", u.getPasswordHash())).isTrue();
    assertThat(u.getEmailEnc()).isNotNull().doesNotContain("dr@example.org");
    assertThat(u.getRoles()).containsExactly(Role.ROLE_DOCTOR);
    assertThat(u.getLocale()).isEqualTo("bn");
    assertThatThrownBy(() -> service.createUser(doctor("dr.rahman", "Strong-pass-2026")))
        .hasMessageContaining("identity.staff.username.taken");
  }

  @Test
  void rejectsWeakPasswordPatientRoleAndBadUsername() {
    assertThatThrownBy(() -> service.createUser(doctor("drx", "short")))
        .isInstanceOf(StaffUserService.StaffValidationException.class);
    assertThatThrownBy(
            () ->
                service.createUser(
                    new StaffUserService.NewStaffUser(
                        "p1x", "P", "Strong-pass-2026", Set.of(Role.ROLE_PATIENT), null, null, null)))
        .hasMessageContaining("identity.staff.roles.patient");
    assertThatThrownBy(() -> service.createUser(doctor("x y", "Strong-pass-2026")))
        .hasMessageContaining("identity.staff.username.invalid");
  }

  @Test
  void rolesPractitionerLinkAndAdminSafeguards() {
    AppUser u = service.createUser(doctor("drk", "Strong-pass-2026"));
    service.assignRoles(u.getId(), Set.of(Role.ROLE_DOCTOR, Role.ROLE_CLINICAL_APPROVER));
    assertThat(u.getRoles()).containsExactlyInAnyOrder(Role.ROLE_DOCTOR, Role.ROLE_CLINICAL_APPROVER);

    Practitioner p = service.createPractitioner(u.getId(), null, "Cardiology", "BMDC-1");
    assertThat(p.getUserId()).isEqualTo(u.getId());
    assertThat(p.getName()).isEqualTo("Dr Rahman");

    Practitioner other = new Practitioner();
    other.setName("Dr Other");
    other.setUserId(UUID.randomUUID());
    practitioners.put(UUID.randomUUID(), other);
    other.setId(practitioners.keySet().stream().filter(k -> practitioners.get(k) == other).findFirst().orElseThrow());
    assertThatThrownBy(() -> service.linkPractitioner(u.getId(), other.getId()))
        .hasMessageContaining("identity.staff.practitioner.linked");

    AppUser admin = new AppUser();
    admin.setId(adminId);
    admin.setUsername("admin");
    admin.setRoles(Set.of(Role.ROLE_ADMIN));
    users.put(adminId, admin);
    assertThatThrownBy(() -> service.assignRoles(adminId, Set.of(Role.ROLE_NURSE)))
        .hasMessageContaining("identity.staff.selfDemote");
    assertThatThrownBy(() -> service.setEnabled(adminId, false)).hasMessageContaining("identity.staff.selfDisable");

    service.setEnabled(u.getId(), false);
    assertThat(u.getEnabled()).isFalse();
    service.resetMfa(u.getId());
    verify(mfa).reset(u.getId(), adminId);
  }
}
