package com.mediflow.patient.web;

import com.mediflow.patient.service.DuplicatePatientException;
import com.mediflow.patient.service.PatientRuleException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(basePackages = "com.mediflow.patient.web")
public class PatientExceptionAdvice {

  @ExceptionHandler(DuplicatePatientException.class)
  public String duplicate(
      DuplicatePatientException ex, HttpServletRequest request, RedirectAttributes flash) {
    flash.addFlashAttribute("errorKey", ex.messageKey());
    return "redirect:" + safeReturn(request);
  }

  @ExceptionHandler(PatientRuleException.class)
  public String rule(PatientRuleException ex, HttpServletRequest request, RedirectAttributes flash) {
    flash.addFlashAttribute("errorKey", ex.messageKey());
    return "redirect:" + safeReturn(request);
  }

  private static String safeReturn(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri != null && uri.startsWith("/patients")) {
      return uri;
    }
    return "/patients/search";
  }
}
