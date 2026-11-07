package com.mediflow.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Renders HTML error pages without leaking internals (no messages / stack traces to the client).
 * Modules may add their own {@code @ControllerAdvice} for module-specific exception types.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AccessDeniedException.class)
  public ModelAndView accessDenied(AccessDeniedException ex, HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken) {
      // Let Spring Security's ExceptionTranslationFilter send anonymous users to the login page.
      throw ex;
    }
    log.warn("Access denied for {} at {}", auth.getName(), request.getRequestURI());
    return view("error/403", HttpStatus.FORBIDDEN, request);
  }

  @ExceptionHandler(Exception.class)
  public ModelAndView handleGeneric(Exception ex, HttpServletRequest request) {
    // Framework exceptions that carry their own status (404 no resource, 405, 400 binding, ...).
    if (ex instanceof ErrorResponse errorResponse
        && !errorResponse.getStatusCode().is5xxServerError()) {
      HttpStatusCode status = errorResponse.getStatusCode();
      log.debug("{} at {}: {}", status.value(), request.getRequestURI(), ex.getMessage());
      return view(status.value() == 404 ? "error/404" : "error/4xx", status, request);
    }
    log.error("Unhandled error at {}", request.getRequestURI(), ex);
    return view("error/500", HttpStatus.INTERNAL_SERVER_ERROR, request);
  }

  private static ModelAndView view(
      String template, HttpStatusCode status, HttpServletRequest request) {
    ModelAndView mav = new ModelAndView(template);
    mav.setStatus(status);
    mav.addObject("path", request.getRequestURI());
    mav.addObject("status", status.value());
    return mav;
  }
}
