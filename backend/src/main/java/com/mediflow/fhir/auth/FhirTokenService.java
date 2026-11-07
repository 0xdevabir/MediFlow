package com.mediflow.fhir.auth;

import com.mediflow.fhir.domain.FhirAccessToken;
import com.mediflow.fhir.domain.FhirOAuthClient;
import com.mediflow.fhir.repo.FhirAccessTokenRepository;
import com.mediflow.fhir.repo.FhirOAuthClientRepository;
import com.mediflow.shared.port.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FhirTokenService {

  public static final Duration TOKEN_TTL = Duration.ofHours(1);

  private final FhirOAuthClientRepository clients;
  private final FhirAccessTokenRepository tokens;
  private final PasswordEncoder passwordEncoder;
  private final ClockPort clock;
  private final SecureRandom random = new SecureRandom();

  public FhirTokenService(
      FhirOAuthClientRepository clients,
      FhirAccessTokenRepository tokens,
      PasswordEncoder passwordEncoder,
      ClockPort clock) {
    this.clients = clients;
    this.tokens = tokens;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  public Optional<Map<String, Object>> issueClientCredentials(String clientId, String clientSecret) {
    if (clientId == null || clientSecret == null) {
      return Optional.empty();
    }
    Optional<FhirOAuthClient> found = clients.findByClientIdAndActiveTrue(clientId.strip());
    if (found.isEmpty()) {
      return Optional.empty();
    }
    FhirOAuthClient client = found.get();
    if (!passwordEncoder.matches(clientSecret, client.getClientSecretHash())) {
      return Optional.empty();
    }
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String accessToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    Instant now = clock.now();
    FhirAccessToken row = new FhirAccessToken();
    row.setClientId(client.getId());
    row.setTokenHash(sha256(accessToken));
    row.setExpiresAt(now.plus(TOKEN_TTL));
    tokens.save(row);
    return Optional.of(
        Map.of(
            "access_token",
            accessToken,
            "token_type",
            "Bearer",
            "expires_in",
            TOKEN_TTL.toSeconds(),
            "scope",
            "fhir/r4.read"));
  }

  @Transactional(readOnly = true)
  public Optional<FhirClientAuthentication> authenticateBearer(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    Instant now = clock.now();
    return tokens
        .findByTokenHashAndExpiresAtAfter(sha256(rawToken.strip()), now)
        .flatMap(
            t ->
                clients
                    .findById(t.getClientId())
                    .filter(FhirOAuthClient::isActive)
                    .map(
                        c ->
                            new FhirClientAuthentication(
                                c.getId(), c.getClientId(), c.getDisplayName())));
  }

  static String sha256(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
