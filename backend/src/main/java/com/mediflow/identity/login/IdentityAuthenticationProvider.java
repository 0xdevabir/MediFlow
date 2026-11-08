package com.mediflow.identity.login;

import com.mediflow.identity.password.PasswordHasher;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.repo.AppUserRepository;
import java.util.stream.Collectors;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Username/password authentication with account lock-out (5 failures → 15 min) and transparent
 * hash upgrade to the identity default algorithm. Being the only {@code AuthenticationProvider}
 * bean, Spring Security uses it for the global {@code AuthenticationManager} (form login).
 * V2-02 keeps this as the explicit security collaborator: no parallel {@code DaoAuthenticationProvider}
 * is registered; T00 {@code SecurityConfig} relies on component-scanned providers.
 *
 * <p>The locked check runs before the password check, so a locked account gives no signal about
 * whether the password was right. The login page shows the same generic error for both.
 */
@Component
public class IdentityAuthenticationProvider extends DaoAuthenticationProvider {

  private final LoginAttemptService attempts;

  public IdentityAuthenticationProvider(
      AppUserRepository users,
      LoginAttemptService attempts,
      PasswordHasher hasher,
      TransactionTemplate tx) {
    super(hasher.encoder());
    this.attempts = attempts;
    setUserDetailsService(username -> load(users, attempts, username));
    setUserDetailsPasswordService(
        (user, newHash) -> {
          tx.executeWithoutResult(
              status ->
                  users
                      .findByUsername(user.getUsername())
                      .ifPresent(
                          u -> {
                            u.setPasswordHash(newHash);
                            users.save(u);
                          }));
          return User.withUserDetails(user).password(newHash).build();
        });
  }

  static UserDetails load(AppUserRepository users, LoginAttemptService attempts, String username) {
    AppUser user =
        users
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    return User.withUsername(user.getUsername())
        .password(user.getPasswordHash())
        .disabled(!user.getEnabled())
        .accountLocked(attempts.isLocked(user.getId()))
        .authorities(
            user.getRoles().stream()
                .map(Role::name)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet()))
        .build();
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String username = authentication.getName();
    try {
      Authentication result = super.authenticate(authentication);
      attempts.onSuccess(username);
      return result;
    } catch (BadCredentialsException e) {
      if (username != null && !username.isBlank()) {
        attempts.onFailure(username);
      }
      throw e;
    }
  }
}
