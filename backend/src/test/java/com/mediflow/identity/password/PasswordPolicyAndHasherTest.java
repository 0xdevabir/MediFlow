package com.mediflow.identity.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordPolicyAndHasherTest {

  @Test
  void policy() {
    assertThat(PasswordPolicy.violations("short1", "bob")).containsExactly("identity.password.policy.length");
    assertThat(PasswordPolicy.violations(null, "bob")).containsExactly("identity.password.policy.length");
    assertThat(PasswordPolicy.violations("onlyletterslong", "bob")).contains("identity.password.policy.mix");
    assertThat(PasswordPolicy.violations("123456789012345", "bob")).contains("identity.password.policy.mix");
    assertThat(PasswordPolicy.violations("doctor-secret-42", "doctor"))
        .contains("identity.password.policy.username");
    assertThat(PasswordPolicy.violations("Password1234".toLowerCase().replace("1234", "123"), "x"))
        .isNotEmpty();
    assertThat(PasswordPolicy.violations("mediflow1234", "x")).contains("identity.password.policy.common");
    assertThat(PasswordPolicy.violations("a1".repeat(65), "x")).contains("identity.password.policy.tooLong");
    assertThat(PasswordPolicy.violations("Correct-Horse-42", "doctor")).isEmpty();
  }

  @Test
  void legacyBcryptMatchesAndNewHashesArePrefixed() {
    PasswordHasher hasher = new PasswordHasher(false);
    String legacy = new BCryptPasswordEncoder().encode("password");
    assertThat(hasher.matches("password", legacy)).isTrue();
    assertThat(hasher.matches("wrong", legacy)).isFalse();
    assertThat(hasher.encoder().upgradeEncoding(legacy)).isTrue();

    String fresh = hasher.hash("Correct-Horse-42");
    assertThat(fresh).startsWith("{bcrypt}");
    assertThat(hasher.matches("Correct-Horse-42", fresh)).isTrue();
    assertThat(hasher.matches("x", null)).isFalse();
  }

  @Test
  void argon2IsDefaultOnlyWhenBouncyCastlePresent() {
    PasswordHasher detected = new PasswordHasher();
    boolean bc =
        org.springframework.util.ClassUtils.isPresent(PasswordHasher.ARGON2_PROBE, getClass().getClassLoader());
    assertThat(detected.defaultAlgorithm()).isEqualTo(bc ? "argon2" : "bcrypt");
  }
}
