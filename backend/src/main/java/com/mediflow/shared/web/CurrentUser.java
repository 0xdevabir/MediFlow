package com.mediflow.shared.web;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.repo.AppUserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Access to the signed-in user. Inject it in services/controllers, or use {@code
 * ${@currentUser.username()}} in templates.
 */
@Component("currentUser")
public class CurrentUser {

  private final AppUserRepository users;

  public CurrentUser(AppUserRepository users) {
    this.users = users;
  }

  /** {@code app_user.id} of the signed-in user, e.g. the actor for {@code AuditPort.record}. */
  public Optional<UUID> userId() {
    return appUser().map(AppUser::getId);
  }

  public Optional<AppUser> appUser() {
    return username().flatMap(users::findByUsername);
  }

  /** Like {@link #userId()} but throws when nobody is signed in. */
  public UUID requireUserId() {
    return userId().orElseThrow(() -> new IllegalStateException("No authenticated user"));
  }

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
