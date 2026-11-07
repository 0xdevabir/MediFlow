package com.mediflow.lab.web;

import com.mediflow.lab.service.LabRuleException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice(basePackages = "com.mediflow.lab.web")
public class LabExceptionAdvice {

  @ExceptionHandler(LabRuleException.class)
  public RedirectView rule(
      LabRuleException ex, HttpServletRequest request, RedirectAttributes flash) {
    flash.addFlashAttribute("errorKey", ex.getMessageKey());
    String referer = request.getHeader("Referer");
    if (referer == null || referer.isBlank()) {
      referer = "/lab/home";
    }
    return new RedirectView(referer);
  }
}
