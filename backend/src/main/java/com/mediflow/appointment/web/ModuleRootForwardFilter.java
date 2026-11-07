package com.mediflow.appointment.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * T00's {@code PlaceholderController} still owns GET {@code /appointments} and {@code /queue}.
 * Until that mapping is removed, forward those exact paths (nav targets) to T04's real pages.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ModuleRootForwardFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!"GET".equalsIgnoreCase(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }
    String path = request.getRequestURI();
    String context = request.getContextPath() == null ? "" : request.getContextPath();
    String relative = path.startsWith(context) ? path.substring(context.length()) : path;
    if ("/appointments".equals(relative)) {
      request.getRequestDispatcher("/appointments/home").forward(request, response);
      return;
    }
    if ("/queue".equals(relative)) {
      request.getRequestDispatcher("/queue/home").forward(request, response);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
