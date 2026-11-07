package com.mediflow.shared.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

  @Column(name = "username", nullable = false, unique = true, length = 100)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "email_enc")
  private String emailEnc;

  @Column(name = "phone_enc")
  private String phoneEnc;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "mfa_enabled", nullable = false)
  private boolean mfaEnabled;

  @Column(name = "locale", nullable = false, length = 10)
  private String locale;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 64)
  private Set<Role> roles = new HashSet<>();

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getEmailEnc() {
    return emailEnc;
  }

  public void setEmailEnc(String emailEnc) {
    this.emailEnc = emailEnc;
  }

  public String getPhoneEnc() {
    return phoneEnc;
  }

  public void setPhoneEnc(String phoneEnc) {
    this.phoneEnc = phoneEnc;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean getMfaEnabled() {
    return mfaEnabled;
  }

  public void setMfaEnabled(boolean mfaEnabled) {
    this.mfaEnabled = mfaEnabled;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public Set<Role> getRoles() {
    return roles;
  }

  public void setRoles(Set<Role> roles) {
    this.roles = roles;
  }

}
