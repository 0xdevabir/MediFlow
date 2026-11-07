package com.mediflow.identity.mfa;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Turns {@link MfaStepUpRequiredException} into a redirect to the step-up page (back to the current
 * page for GET, to the referring page for POST so the user re-submits after TOTP), or 401 for
 * {@code /api/**}.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MfaWebAdvice {

  @ExceptionHandler(MfaStepUpRequiredException.class)
  public ModelAndView stepUp(HttpServletRequest request, HttpServletResponse response) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (path.startsWith("/api/")) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setHeader("X-MFA-Step-Up", "/identity/mfa/step-up");
      return null;
    }
    String back;
    if ("GET".equalsIgnoreCase(request.getMethod())) {
      String q = request.getQueryString();
      back = SafeRedirect.localOrRoot(q == null ? path : path + "?" + q);
    } else {
      back = SafeRedirect.fromReferer(request.getHeader("Referer"), request.getServerName());
    }
    return new ModelAndView(
        "redirect:/identity/mfa/step-up?continue=" + URLEncoder.encode(back, StandardCharsets.UTF_8));
  }
}
