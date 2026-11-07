package com.mediflow.identity.mfa;

import java.net.URI;

/** Only local, relative redirect targets (no open redirect). */
public final class SafeRedirect {

  private SafeRedirect() {}

  public static String localOrRoot(String target) {
    if (target == null || target.isBlank()) {
      return "/";
    }
    String t = target.trim();
    if (!t.startsWith("/") || t.startsWith("//") || t.contains("\\") || t.contains("\r")
        || t.contains("\n") || t.startsWith("/identity/mfa")) {
      return "/";
    }
    try {
      URI uri = new URI(t);
      if (uri.getScheme() != null || uri.getAuthority() != null) {
        return "/";
      }
    } catch (Exception e) {
      return "/";
    }
    return t;
  }

  /** Path + query of a same-origin Referer header, else "/". */
  public static String fromReferer(String referer, String expectedHost) {
    if (referer == null) {
      return "/";
    }
    try {
      URI uri = new URI(referer);
      if (uri.getHost() != null && expectedHost != null && !uri.getHost().equalsIgnoreCase(expectedHost)) {
        return "/";
      }
      String path = uri.getRawPath();
      String query = uri.getRawQuery();
      return localOrRoot(query == null ? path : path + "?" + query);
    } catch (Exception e) {
      return "/";
    }
  }
}
