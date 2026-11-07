package com.mediflow.notification.service;

import com.mediflow.notification.domain.NotificationPreference;
import com.mediflow.notification.domain.PushDeviceToken;
import com.mediflow.notification.repo.NotificationPreferenceRepository;
import com.mediflow.notification.repo.PushDeviceTokenRepository;
import com.mediflow.shared.port.AuditPort;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Patient / user notification preferences and push-device registration. */
@Service
public class PreferenceService {

  public static final int MAX_TOKEN_LENGTH = 512;

  private final NotificationPreferenceRepository prefs;
  private final PushDeviceTokenRepository tokens;
  private final AuditPort audit;

  public PreferenceService(
      NotificationPreferenceRepository prefs, PushDeviceTokenRepository tokens, AuditPort audit) {
    this.prefs = prefs;
    this.tokens = tokens;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public NotificationPreference get(UUID userId) {
    return prefs.findByUserId(userId).orElseGet(() -> NotificationPreference.defaultsFor(userId));
  }

  @Transactional
  public NotificationPreference save(
      UUID userId,
      boolean sms,
      boolean email,
      boolean push,
      boolean inApp,
      LocalTime quietStart,
      LocalTime quietEnd,
      String locale) {
    NotificationPreference p =
        prefs.findByUserId(userId).orElseGet(() -> NotificationPreference.defaultsFor(userId));
    p.setSmsEnabled(sms);
    p.setEmailEnabled(email);
    p.setPushEnabled(push);
    p.setInAppEnabled(inApp);
    // quiet hours only make sense as a pair
    boolean both = quietStart != null && quietEnd != null && !quietStart.equals(quietEnd);
    p.setQuietStart(both ? quietStart : null);
    p.setQuietEnd(both ? quietEnd : null);
    p.setLocale(RecipientResolver.normaliseLocale(locale));
    NotificationPreference saved = prefs.save(p);
    audit.record(
        userId, null, "NOTIFICATION_PREFERENCES_UPDATE", "NotificationPreference", saved.getId(),
        Map.of("sms", sms, "email", email, "push", push, "inApp", inApp, "locale", saved.getLocale()));
    return saved;
  }

  /** Registers a web-push token (idempotent). */
  @Transactional
  public void registerPushToken(UUID userId, String token) {
    if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
      throw new IllegalArgumentException("Invalid push token");
    }
    String t = token.trim();
    if (!tokens.existsByUserIdAndToken(userId, t)) {
      tokens.save(new PushDeviceToken(userId, t));
    }
  }

  @Transactional
  public void removePushToken(UUID userId, String token) {
    if (token != null) {
      tokens.deleteByUserIdAndToken(userId, token.trim());
    }
  }
}
