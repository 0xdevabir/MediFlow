package com.mediflow.config;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.repo.AppUserRepository;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevDataLoader {

  private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);
  private static final String DEV_PASSWORD = "password";

  @Bean
  CommandLineRunner seedDevUsers(AppUserRepository users, PasswordEncoder encoder) {
    return args -> {
      seed(users, encoder, "patient", "Patient User", EnumSet.of(Role.ROLE_PATIENT));
      seed(users, encoder, "doctor", "Doctor User", EnumSet.of(Role.ROLE_DOCTOR));
      seed(users, encoder, "nurse", "Nurse User", EnumSet.of(Role.ROLE_NURSE));
      seed(users, encoder, "frontdesk", "Front Desk", EnumSet.of(Role.ROLE_FRONT_DESK));
      seed(users, encoder, "labtech", "Lab Tech", EnumSet.of(Role.ROLE_LAB_TECH));
      seed(users, encoder, "pharmacist", "Pharmacist", EnumSet.of(Role.ROLE_PHARMACIST));
      seed(users, encoder, "er", "ER Staff", EnumSet.of(Role.ROLE_ER));
      seed(users, encoder, "admin", "Administrator", EnumSet.of(Role.ROLE_ADMIN));
      seed(
          users,
          encoder,
          "approver",
          "Clinical Approver",
          EnumSet.of(Role.ROLE_CLINICAL_APPROVER));
      log.info("Dev users seeded (password='{}')", DEV_PASSWORD);
    };
  }

  private static void seed(
      AppUserRepository users, PasswordEncoder encoder, String username, String display, Set<Role> roles) {
    if (users.findByUsername(username).isPresent()) {
      return;
    }
    AppUser user = new AppUser();
    user.setUsername(username);
    user.setPasswordHash(encoder.encode(DEV_PASSWORD));
    user.setDisplayName(display);
    user.setEnabled(true);
    user.setMfaEnabled(false);
    user.setLocale("en");
    user.setRoles(roles);
    users.save(user);
  }
}
