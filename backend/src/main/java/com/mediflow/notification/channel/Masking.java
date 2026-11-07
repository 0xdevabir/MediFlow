package com.mediflow.notification.channel;

/** Masks contact details for logs and staff screens. */
public final class Masking {

  private Masking() {}

  public static String phone(String phone) {
    if (phone == null || phone.length() < 4) {
      return "****";
    }
    return "*".repeat(Math.max(0, phone.length() - 3)) + phone.substring(phone.length() - 3);
  }

  public static String email(String email) {
    if (email == null) {
      return "";
    }
    int at = email.indexOf('@');
    return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
  }
}
