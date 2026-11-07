package com.mediflow.directory.web;

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
 * Forwards {@code GET /directory} to {@code /directory/search} so we do not clash with T00's
 * {@code PlaceholderController} mapping of {@code /directory}. Requested removal of the
 * placeholder mapping is in {@code docs/plan/requests/T03.md}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class DirectoryRootForwardFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if ("GET".equalsIgnoreCase(request.getMethod())) {
      String path = request.getRequestURI();
      String context = request.getContextPath() == null ? "" : request.getContextPath();
      String relative = context.isEmpty() ? path : path.substring(context.length());
      if ("/directory".equals(relative) || "/directory/".equals(relative)) {
        request.getRequestDispatcher("/directory/search").forward(request, response);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }
}
