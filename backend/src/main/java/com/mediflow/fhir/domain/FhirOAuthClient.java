package com.mediflow.fhir.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "fhir_oauth_client")
public class FhirOAuthClient extends BaseEntity {

  @Column(name = "client_id", nullable = false, unique = true, length = 128)
  private String clientId;

  @Column(name = "client_secret_hash", nullable = false)
  private String clientSecretHash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecretHash() {
    return clientSecretHash;
  }

  public void setClientSecretHash(String clientSecretHash) {
    this.clientSecretHash = clientSecretHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
