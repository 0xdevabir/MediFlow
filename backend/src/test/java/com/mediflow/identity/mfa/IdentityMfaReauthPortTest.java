package com.mediflow.identity.mfa;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.shared.port.ClockPort;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class IdentityMfaReauthPortTest {

  private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
  private final ClockPort clock = () -> NOW;
  private final MfaReauthService reauth = new MfaReauthService(clock);
  private final IdentityMfaReauthPort port = new IdentityMfaReauthPort(reauth);

  @BeforeEach
  void signIn() {
    TestingAuthenticationToken auth = new TestingAuthenticationToken("doctor", "pw", "ROLE_DOCTOR");
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void requiresFreshWithinMaxAge() {
    MockHttpSession session = new MockHttpSession();
    MfaSession.markVerified(session, "doctor", NOW.minus(Duration.ofMinutes(2)));
    bind(session);

    assertThatCode(() -> port.requireFreshMfa(Duration.ofMinutes(5))).doesNotThrowAnyException();
    assertThatThrownBy(() -> port.requireFreshMfa(Duration.ofMinutes(1)))
        .isInstanceOf(MfaStepUpRequiredException.class);
  }

  @Test
  void deniesWithoutSessionVerification() {
    bind(new MockHttpSession());
    assertThatThrownBy(() -> port.requireFreshMfa(Duration.ofMinutes(5)))
        .isInstanceOf(MfaStepUpRequiredException.class);
  }

  private static void bind(HttpSession session) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSession((MockHttpSession) session);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
