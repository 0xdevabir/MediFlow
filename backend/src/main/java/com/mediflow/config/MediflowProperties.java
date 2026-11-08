package com.mediflow.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mediflow")
public class MediflowProperties {

  private String fieldEncryptionKey = "";
  private final Minio minio = new Minio();
  private final Patient patient = new Patient();

  public String getFieldEncryptionKey() {
    return fieldEncryptionKey;
  }

  public void setFieldEncryptionKey(String fieldEncryptionKey) {
    this.fieldEncryptionKey = fieldEncryptionKey;
  }

  public Minio getMinio() {
    return minio;
  }

  public Patient getPatient() {
    return patient;
  }

  public static class Patient {
    /** Default TTL for {@code PatientQrPort#issueToken} when callers omit an override. */
    private Duration qrTokenTtl = Duration.ofMinutes(15);

    public Duration getQrTokenTtl() {
      return qrTokenTtl;
    }

    public void setQrTokenTtl(Duration qrTokenTtl) {
      this.qrTokenTtl = qrTokenTtl;
    }
  }

  public static class Minio {
    private String endpoint = "http://localhost:9000";
    private String accessKey = "mediflow";
    private String secretKey = "mediflowsecret";
    private String bucket = "mediflow";

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getAccessKey() {
      return accessKey;
    }

    public void setAccessKey(String accessKey) {
      this.accessKey = accessKey;
    }

    public String getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(String bucket) {
      this.bucket = bucket;
    }
  }
}
