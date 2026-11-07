package com.mediflow.config;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.repo.AppUserRepository;
import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MediflowUserDetailsService implements UserDetailsService {

  private final AppUserRepository appUserRepository;

  public MediflowUserDetailsService(AppUserRepository appUserRepository) {
    this.appUserRepository = appUserRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    AppUser user =
        appUserRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    return User.withUsername(user.getUsername())
        .password(user.getPasswordHash())
        .disabled(!user.getEnabled())
        .authorities(
            user.getRoles().stream()
                .map(Role::name)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet()))
        .build();
  }
}

