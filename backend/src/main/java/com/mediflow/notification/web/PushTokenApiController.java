package com.mediflow.notification.web;

import com.mediflow.notification.service.PreferenceService;
import com.mediflow.shared.web.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web-push device registration (FCM token from the browser's service worker). */
@RestController
@RequestMapping("/api/notifications/push-tokens")
@PreAuthorize("isAuthenticated()")
public class PushTokenApiController {

  public record TokenRequest(String token) {}

  private final PreferenceService preferences;
  private final CurrentUser currentUser;

  public PushTokenApiController(PreferenceService preferences, CurrentUser currentUser) {
    this.preferences = preferences;
    this.currentUser = currentUser;
  }

  @PostMapping
  public ResponseEntity<Void> register(@RequestBody TokenRequest req) {
    try {
      preferences.registerPushToken(currentUser.requireUserId(), req.token());
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @DeleteMapping
  public ResponseEntity<Void> remove(@RequestBody TokenRequest req) {
    preferences.removePushToken(currentUser.requireUserId(), req.token());
    return ResponseEntity.noContent().build();
  }
}
