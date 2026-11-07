package com.mediflow.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ModelAndView accessDenied(AccessDeniedException ex, HttpServletRequest request) {
    log.warn("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());
    ModelAndView mav = new ModelAndView("error/403");
    mav.addObject("path", request.getRequestURI());
    return mav;
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ModelAndView handleGeneric(Exception ex, HttpServletRequest request, Model model) {
    log.error("Unhandled error at {}", request.getRequestURI(), ex);
    ModelAndView mav = new ModelAndView("error/500");
    mav.addObject("path", request.getRequestURI());
    return mav;
  }
}
