package com.mediflow.fhir.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Machine principal for OAuth2 client-credentials access to `/fhir/r4/**`. */
public final class FhirClientAuthentication implements Authentication {

  public static final String ROLE_FHIR_CLIENT = "ROLE_FHIR_CLIENT";

  private final UUID clientRowId;
  private final String clientId;
  private final String displayName;
  private boolean authenticated = true;

  public FhirClientAuthentication(UUID clientRowId, String clientId, String displayName) {
    this.clientRowId = clientRowId;
    this.clientId = clientId;
    this.displayName = displayName;
  }

  public UUID clientRowId() {
    return clientRowId;
  }

  public String clientId() {
    return clientId;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(ROLE_FHIR_CLIENT));
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getDetails() {
    return displayName;
  }

  @Override
  public Object getPrincipal() {
    return clientId;
  }

  @Override
  public boolean isAuthenticated() {
    return authenticated;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) {
    this.authenticated = isAuthenticated;
  }

  @Override
  public String getName() {
    return clientId;
  }
}
