package com.mediflow.config;

import com.mediflow.shared.encryption.FieldEncryptionKeyHolder;
import jakarta.annotation.PostConstruct;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptionConfig {

  private static final Logger log = LoggerFactory.getLogger(EncryptionConfig.class);

  private final MediflowProperties properties;

  public EncryptionConfig(MediflowProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void initKey() {
    String base64 = properties.getFieldEncryptionKey();
    if (base64 == null || base64.isBlank()) {
      log.warn(
          "MEDIFLOW_FIELD_ENCRYPTION_KEY not set; using zero key (dev/test only)");
      FieldEncryptionKeyHolder.setKey(new byte[32]);
      return;
    }
    byte[] keyBytes = Base64.getDecoder().decode(base64.trim());
    FieldEncryptionKeyHolder.setKey(keyBytes);
    log.info("Field encryption key loaded");
  }
}
