package com.mediflow.notification.channel;

import java.util.List;
import java.util.UUID;

/** Resolved addresses of one recipient (decrypted in memory only, never persisted). */
public record Contact(
    UUID userId, UUID patientId, String displayName, String phone, String email, List<String> pushTokens) {

  public Contact {
    pushTokens = pushTokens == null ? List.of() : List.copyOf(pushTokens);
  }

  public boolean hasPhone() {
    return phone != null && !phone.isBlank();
  }

  public boolean hasEmail() {
    return email != null && !email.isBlank();
  }
}
