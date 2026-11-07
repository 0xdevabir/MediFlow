package com.mediflow.prescription.web;

import com.mediflow.prescription.support.RxExceptions.MfaStepUpRequiredException;
import com.mediflow.prescription.support.RxExceptions.RxException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice(basePackages = "com.mediflow.prescription")
public class RxWebAdvice {

  @ExceptionHandler(MfaStepUpRequiredException.class)
  public RedirectView mfaStepUp(HttpServletRequest request) {
    String cont = request.getRequestURI();
    String qs = request.getQueryString();
    if (qs != null) {
      cont = cont + "?" + qs;
    }
    return new RedirectView("/identity/mfa/step-up?continue=" + cont, true);
  }

  @ExceptionHandler(RxException.class)
  public RedirectView rxError(RxException ex, RedirectAttributes attrs, HttpServletRequest request) {
    attrs.addFlashAttribute("errorKey", ex.messageKey());
    attrs.addFlashAttribute("errorMessage", ex.getMessage());
    String referer = request.getHeader("Referer");
    if (referer != null && !referer.isBlank()) {
      return new RedirectView(referer, true);
    }
    return new RedirectView("/prescriptions", true);
  }
}
