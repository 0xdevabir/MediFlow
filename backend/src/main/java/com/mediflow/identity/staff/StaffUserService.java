package com.mediflow.identity.staff;

import com.mediflow.identity.login.LoginAttemptService;
import com.mediflow.identity.mfa.MfaService;
import com.mediflow.identity.password.PasswordHasher;
import com.mediflow.identity.password.PasswordPolicy;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.encryption.AesGcmAttributeConverter;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.shared.repo.PractitionerRepository;
import com.mediflow.shared.web.CurrentUser;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff account management for the admin console (T11). Admin only. Patients get their logins via
 * T02 registration, so {@link Role#ROLE_PATIENT} cannot be assigned here. Passwords are checked
 * against {@link PasswordPolicy} and hashed with {@link PasswordHasher}; e-mail / phone are
 * AES-GCM encrypted. Every change is audited.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class StaffUserService {

  public static final Set<Role> STAFF_ROLES = EnumSet.complementOf(EnumSet.of(Role.ROLE_PATIENT));

  /** Rejected input; {@code messageKeys} are identity_* message keys. */
  public static class StaffValidationException extends RuntimeException {
    private final List<String> messageKeys;

    public StaffValidationException(List<String> messageKeys) {
      super(String.join(",", messageKeys));
      this.messageKeys = List.copyOf(messageKeys);
    }

    public List<String> messageKeys() {
      return messageKeys;
    }
  }

  /** Input for {@link #createUser}. {@code email}, {@code phone}, {@code locale} are optional. */
  public record NewStaffUser(
      String username,
      String displayName,
      String initialPassword,
      Set<Role> roles,
      String email,
      String phone,
      String locale) {}

  private final AppUserRepository users;
  private final PractitionerRepository practitioners;
  private final PasswordHasher hasher;
  private final MfaService mfa;
  private final LoginAttemptService loginAttempts;
  private final AuditPort audit;
  private final CurrentUser currentUser;
  private final AesGcmAttributeConverter crypto = new AesGcmAttributeConverter();

  public StaffUserService(
      AppUserRepository users,
      PractitionerRepository practitioners,
      PasswordHasher hasher,
      MfaService mfa,
      LoginAttemptService loginAttempts,
      AuditPort audit,
      CurrentUser currentUser) {
    this.users = users;
    this.practitioners = practitioners;
    this.hasher = hasher;
    this.mfa = mfa;
    this.loginAttempts = loginAttempts;
    this.audit = audit;
    this.currentUser = currentUser;
  }

  @Transactional
  public AppUser createUser(NewStaffUser in) {
    String username = in.username() == null ? "" : in.username().strip().toLowerCase();
    if (!username.matches("[a-z0-9._-]{3,100}")) {
      throw new StaffValidationException(List.of("identity.staff.username.invalid"));
    }
    if (users.findByUsername(username).isPresent()) {
      throw new StaffValidationException(List.of("identity.staff.username.taken"));
    }
    if (in.displayName() == null || in.displayName().isBlank()) {
      throw new StaffValidationException(List.of("identity.staff.displayName.required"));
    }
    Set<Role> roles = validRoles(in.roles());
    List<String> violations = PasswordPolicy.violations(in.initialPassword(), username);
    if (!violations.isEmpty()) {
      throw new StaffValidationException(violations);
    }
    AppUser u = new AppUser();
    u.setUsername(username);
    u.setDisplayName(in.displayName().strip());
    u.setPasswordHash(hasher.hash(in.initialPassword()));
    u.setEmailEnc(blank(in.email()) ? null : crypto.convertToDatabaseColumn(in.email().strip()));
    u.setPhoneEnc(blank(in.phone()) ? null : crypto.convertToDatabaseColumn(in.phone().strip()));
    u.setLocale("bn".equals(in.locale()) ? "bn" : "en");
    u.setEnabled(true);
    u.setMfaEnabled(false); // MFA-required roles are forced to enrol at first login
    u.setRoles(roles);
    AppUser saved = users.save(u);
    audit.record(
        actor(), null, "STAFF_USER_CREATED", "AppUser", saved.getId(),
        Map.of("username", username, "roles", roleNames(roles)));
    return saved;
  }

  @Transactional
  public AppUser assignRoles(UUID userId, Set<Role> roles) {
    AppUser u = load(userId);
    if (u.getRoles().contains(Role.ROLE_PATIENT)) {
      throw new StaffValidationException(List.of("identity.staff.patientAccount"));
    }
    Set<Role> valid = validRoles(roles);
    if (userId.equals(actor()) && !valid.contains(Role.ROLE_ADMIN)) {
      // An admin cannot remove their own admin role (avoids locking the console out).
      throw new StaffValidationException(List.of("identity.staff.selfDemote"));
    }
    Set<Role> before = Set.copyOf(u.getRoles());
    u.setRoles(valid);
    users.save(u);
    audit.record(
        actor(), null, "STAFF_ROLES_CHANGED", "AppUser", userId,
        Map.of("before", roleNames(before), "after", roleNames(valid)));
    return u;
  }

  /** Links an existing practitioner (directory entry) to the user account. */
  @Transactional
  public Practitioner linkPractitioner(UUID userId, UUID practitionerId) {
    load(userId);
    Practitioner p =
        practitioners
            .findById(practitionerId)
            .orElseThrow(() -> new StaffValidationException(List.of("identity.staff.practitioner.unknown")));
    if (p.getUserId() != null && !p.getUserId().equals(userId)) {
      throw new StaffValidationException(List.of("identity.staff.practitioner.linked"));
    }
    p.setUserId(userId);
    Practitioner saved = practitioners.save(p);
    audit.record(actor(), null, "PRACTITIONER_LINKED", "Practitioner", practitionerId, Map.of("userId", userId.toString()));
    return saved;
  }

  /** Creates a practitioner entry for the user (e.g. a new doctor). */
  @Transactional
  public Practitioner createPractitioner(UUID userId, String name, String roleLabel, String licenceNo) {
    AppUser u = load(userId);
    Practitioner p = new Practitioner();
    p.setUserId(userId);
    p.setName(blank(name) ? u.getDisplayName() : name.strip());
    p.setRoleLabel(blank(roleLabel) ? null : roleLabel.strip());
    p.setLicenceNo(blank(licenceNo) ? null : licenceNo.strip());
    Practitioner saved = practitioners.save(p);
    audit.record(actor(), null, "PRACTITIONER_CREATED", "Practitioner", saved.getId(), Map.of("userId", userId.toString()));
    return saved;
  }

  @Transactional
  public void setEnabled(UUID userId, boolean enabled) {
    AppUser u = load(userId);
    if (!enabled && userId.equals(actor())) {
      throw new StaffValidationException(List.of("identity.staff.selfDisable"));
    }
    u.setEnabled(enabled);
    users.save(u);
    audit.record(actor(), null, enabled ? "STAFF_USER_ENABLED" : "STAFF_USER_DISABLED", "AppUser", userId, Map.of());
  }

  /** Removes the TOTP secret; the user re-enrols at next sign-in (lost phone). */
  @Transactional
  public void resetMfa(UUID userId) {
    load(userId);
    mfa.reset(userId, actor());
  }

  @Transactional
  public void unlock(UUID userId) {
    load(userId);
    loginAttempts.unlock(userId);
    audit.record(actor(), null, "STAFF_USER_UNLOCKED", "AppUser", userId, Map.of());
  }

  @Transactional(readOnly = true)
  public List<AppUser> listStaff() {
    return users.findAll().stream().filter(u -> !u.getRoles().contains(Role.ROLE_PATIENT)).toList();
  }

  private Set<Role> validRoles(Set<Role> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new StaffValidationException(List.of("identity.staff.roles.required"));
    }
    if (!STAFF_ROLES.containsAll(roles)) {
      throw new StaffValidationException(List.of("identity.staff.roles.patient"));
    }
    return EnumSet.copyOf(roles);
  }

  private AppUser load(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new StaffValidationException(List.of("identity.staff.unknown")));
  }

  private UUID actor() {
    return currentUser.userId().orElse(null);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static List<String> roleNames(Set<Role> roles) {
    return roles.stream().map(Enum::name).sorted().toList();
  }
}
