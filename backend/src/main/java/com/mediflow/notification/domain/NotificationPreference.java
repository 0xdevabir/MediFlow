package com.mediflow.notification.domain;

import com.mediflow.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "notification_preference")
public class NotificationPreference extends BaseEntity {

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "sms_enabled", nullable = false)
  private boolean smsEnabled = true;

  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled = true;

  @Column(name = "push_enabled", nullable = false)
  private boolean pushEnabled = true;

  @Column(name = "in_app_enabled", nullable = false)
  private boolean inAppEnabled = true;

  @Column(name = "quiet_start")
  private LocalTime quietStart;

  @Column(name = "quiet_end")
  private LocalTime quietEnd;

  @Column(name = "locale")
  private String locale;

  public static NotificationPreference defaultsFor(UUID userId) {
    NotificationPreference p = new NotificationPreference();
    p.userId = userId;
    return p;
  }

  public boolean allows(Channel channel) {
    return switch (channel) {
      case SMS -> smsEnabled;
      case EMAIL -> emailEnabled;
      case PUSH -> pushEnabled;
      case IN_APP -> inAppEnabled;
    };
  }

  /** True when {@code time} falls in [quietStart, quietEnd), handling windows over midnight. */
  public boolean isQuiet(LocalTime time) {
    if (quietStart == null || quietEnd == null || quietStart.equals(quietEnd)) {
      return false;
    }
    if (quietStart.isBefore(quietEnd)) {
      return !time.isBefore(quietStart) && time.isBefore(quietEnd);
    }
    return !time.isBefore(quietStart) || time.isBefore(quietEnd);
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public boolean isSmsEnabled() {
    return smsEnabled;
  }

  public void setSmsEnabled(boolean smsEnabled) {
    this.smsEnabled = smsEnabled;
  }

  public boolean isEmailEnabled() {
    return emailEnabled;
  }

  public void setEmailEnabled(boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
  }

  public boolean isPushEnabled() {
    return pushEnabled;
  }

  public void setPushEnabled(boolean pushEnabled) {
    this.pushEnabled = pushEnabled;
  }

  public boolean isInAppEnabled() {
    return inAppEnabled;
  }

  public void setInAppEnabled(boolean inAppEnabled) {
    this.inAppEnabled = inAppEnabled;
  }

  public LocalTime getQuietStart() {
    return quietStart;
  }

  public void setQuietStart(LocalTime quietStart) {
    this.quietStart = quietStart;
  }

  public LocalTime getQuietEnd() {
    return quietEnd;
  }

  public void setQuietEnd(LocalTime quietEnd) {
    this.quietEnd = quietEnd;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }
}
