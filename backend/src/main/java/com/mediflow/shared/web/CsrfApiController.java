package com.mediflow.shared.web;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands the Next.js frontend the current (masked) CSRF token. Spring Security 6's default XOR
 * handler rejects the raw XSRF-TOKEN cookie value as a header, so SPA requests send this value in
 * X-XSRF-TOKEN (or as the _csrf form field on login). Public: the login form needs it pre-auth.
 */
@RestController
public class CsrfApiController {

  @GetMapping("/api/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of(
        "headerName", token.getHeaderName(),
        "parameterName", token.getParameterName(),
        "token", token.getToken());
  }
}
