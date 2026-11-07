package com.mediflow.notification.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Delivery channels. {@link #ESCALATION_ORDER} is the R7 fallback chain for external channels. */
public enum Channel {
  PUSH,
  SMS,
  EMAIL,
  IN_APP;

  public static final List<Channel> ESCALATION_ORDER = List.of(PUSH, SMS, EMAIL, IN_APP);

  public boolean external() {
    return this != IN_APP;
  }

  public String routingKey() {
    return "notify." + name().toLowerCase(Locale.ROOT);
  }

  public static Optional<Channel> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String v = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    if (v.equals("INAPP") || v.equals("WEB")) {
      v = "IN_APP";
    }
    try {
      return Optional.of(Channel.valueOf(v));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
