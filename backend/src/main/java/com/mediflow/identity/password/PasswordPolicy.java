package com.mediflow.identity.password;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Password policy: 12–128 characters, at least one letter and one digit, not containing the
 * username, not a well-known password. Returns message keys (identity_*.properties).
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 128;

  private static final Set<String> COMMON =
      Set.of(
          "password", "password1", "password123", "123456789012", "qwertyuiop12", "letmein12345",
          "welcome12345", "admin1234567", "mediflow1234", "iloveyou1234", "passw0rd1234",
          "changeme1234", "abc123456789", "111111111111", "000000000000");

  private PasswordPolicy() {}

  public static List<String> violations(String password, String username) {
    List<String> errors = new ArrayList<>();
    if (password == null || password.length() < MIN_LENGTH) {
      errors.add("identity.password.policy.length");
      return errors;
    }
    if (password.length() > MAX_LENGTH) {
      errors.add("identity.password.policy.tooLong");
    }
    if (password.chars().noneMatch(Character::isLetter)
        || password.chars().noneMatch(Character::isDigit)) {
      errors.add("identity.password.policy.mix");
    }
    String lower = password.toLowerCase(Locale.ROOT);
    if (username != null
        && username.length() >= 3
        && lower.contains(username.toLowerCase(Locale.ROOT))) {
      errors.add("identity.password.policy.username");
    }
    if (COMMON.contains(lower)) {
      errors.add("identity.password.policy.common");
    }
    return errors;
  }
}
