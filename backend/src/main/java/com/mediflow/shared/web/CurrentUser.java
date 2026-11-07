package com.mediflow.shared.web;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component("currentUser")
public class CurrentUser {

  public Optional<String> username() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return Optional.empty();
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof UserDetails userDetails) {
      return Optional.of(userDetails.getUsername());
    }
    if (principal instanceof String name && !"anonymousUser".equals(name)) {
      return Optional.of(name);
    }
    return Optional.empty();
  }

  public boolean hasRole(String role) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return false;
    }
    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
    return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
  }
}
