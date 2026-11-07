package com.mediflow.config;

import com.mediflow.shared.encryption.FieldEncryptionKeyHolder;
import jakarta.annotation.PostConstruct;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class EncryptionConfig {

  private static final Logger log = LoggerFactory.getLogger(EncryptionConfig.class);

  private final MediflowProperties properties;
  private final Environment environment;

  public EncryptionConfig(MediflowProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @PostConstruct
  void initKey() {
    String base64 = properties.getFieldEncryptionKey();
    if (base64 == null || base64.isBlank()) {
      if (!environment.acceptsProfiles(Profiles.of("dev", "test"))) {
        throw new IllegalStateException(
            "MEDIFLOW_FIELD_ENCRYPTION_KEY must be set (Base64 of 32 random bytes)");
      }
      log.warn("MEDIFLOW_FIELD_ENCRYPTION_KEY not set; using zero key (dev/test profile only)");
      FieldEncryptionKeyHolder.setKey(new byte[32]);
      return;
    }
    byte[] keyBytes = Base64.getDecoder().decode(base64.trim());
    if (keyBytes.length != 32) {
      throw new IllegalStateException(
          "MEDIFLOW_FIELD_ENCRYPTION_KEY must decode to 32 bytes (AES-256), got " + keyBytes.length);
    }
    FieldEncryptionKeyHolder.setKey(keyBytes);
    log.info("Field encryption key loaded");
  }
}
