package com.mediflow.notification.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "push_device_token")
public class PushDeviceToken extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token", nullable = false)
  private String token;

  protected PushDeviceToken() {}

  public PushDeviceToken(UUID userId, String token) {
    this.userId = userId;
    this.token = token;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getToken() {
    return token;
  }
}
