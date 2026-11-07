package com.mediflow.identity.password;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Password hashing for identity. New hashes are Argon2id ({@code {argon2}...}) when BouncyCastle is
 * on the classpath, otherwise BCrypt ({@code {bcrypt}...}). Legacy un-prefixed BCrypt hashes (the
 * T00 dev seed) still match and are upgraded on the next successful login.
 *
 * <p>Deliberately not exposed as a {@link PasswordEncoder} bean to avoid clashing with the T00 bean;
 * see docs/plan/requests/T01.md.
 */
@Component
public class PasswordHasher {

  private static final Logger log = LoggerFactory.getLogger(PasswordHasher.class);
  static final String ARGON2_PROBE = "org.bouncycastle.crypto.generators.Argon2BytesGenerator";

  private final DelegatingPasswordEncoder encoder;
  private final String defaultId;

  @org.springframework.beans.factory.annotation.Autowired
  public PasswordHasher() {
    this(ClassUtils.isPresent(ARGON2_PROBE, PasswordHasher.class.getClassLoader()));
  }

  /** Test seam: force the default algorithm. */
  public PasswordHasher(boolean argon2Available) {
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    encoders.put("bcrypt", bcrypt);
    if (argon2Available) {
      encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
      defaultId = "argon2";
    } else {
      defaultId = "bcrypt";
      log.warn("BouncyCastle not on classpath: hashing passwords with BCrypt instead of Argon2");
    }
    encoder = new DelegatingPasswordEncoder(defaultId, encoders);
    encoder.setDefaultPasswordEncoderForMatches(bcrypt);
  }

  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  public boolean matches(String rawPassword, String storedHash) {
    return storedHash != null && encoder.matches(rawPassword, storedHash);
  }

  public PasswordEncoder encoder() {
    return encoder;
  }

  public String defaultAlgorithm() {
    return defaultId;
  }
}
