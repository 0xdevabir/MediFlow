package com.mediflow.appointment.web;

import com.mediflow.appointment.support.AppointmentException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice(basePackages = "com.mediflow.appointment.web")
public class AppointmentExceptionAdvice {

  @ExceptionHandler(AppointmentException.class)
  public RedirectView handle(AppointmentException ex, HttpServletRequest request, RedirectAttributes ra) {
    ra.addFlashAttribute("errorKey", ex.messageKey());
    String referer = request.getHeader("Referer");
    if (referer != null && !referer.isBlank()) {
      return new RedirectView(referer, true);
    }
    return new RedirectView("/appointments/home", true);
  }
}

