package com.mediflow.identity.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class TotpAndStepUpTest {

  private final TotpService totp = new TotpService();
  private final Instant t0 = Instant.parse("2026-11-07T06:00:10Z");

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void acceptsCurrentAndAdjacentStepsOnly() {
    String secret = totp.newSecret();
    long step = TotpService.step(t0);
    assertThat(totp.matchingStep(secret, totp.codeAt(secret, step), t0, null)).hasValue(step);
    assertThat(totp.matchingStep(secret, totp.codeAt(secret, step - 1), t0, null)).hasValue(step - 1);
    assertThat(totp.matchingStep(secret, totp.codeAt(secret, step + 1), t0, null)).hasValue(step + 1);
    assertThat(totp.matchingStep(secret, totp.codeAt(secret, step - 2), t0, null)).isEmpty();
    assertThat(totp.matchingStep(secret, "12345", t0, null)).isEmpty();
    assertThat(totp.matchingStep(secret, "abcdef", t0, null)).isEmpty();
    assertThat(totp.matchingStep(secret, null, t0, null)).isEmpty();
  }

  @Test
  void codeCannotBeReplayed() {
    String secret = totp.newSecret();
    long step = TotpService.step(t0);
    String code = totp.codeAt(secret, step);
    assertThat(totp.matchingStep(secret, code, t0, step)).isEmpty();
    assertThat(totp.matchingStep(secret, totp.codeAt(secret, step - 1), t0, step)).isEmpty();
    assertThat(totp.matchingStep(secret, code, t0, step - 1)).hasValue(step);
  }

  @Test
  void qrIsPngDataUri() {
    assertThat(totp.qrDataUri("doctor", totp.newSecret())).startsWith("data:image/png;base64,");
  }

  @Test
  void requireFreshMfaWithinFiveMinutes() {
    AtomicReference<Instant> now = new AtomicReference<>(t0);
    MfaReauthService reauth = new MfaReauthService(now::get);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "doctor", null, AuthorityUtils.createAuthorityList("ROLE_DOCTOR")));

    // never verified
    assertThatThrownBy(reauth::requireFreshMfa).isInstanceOf(MfaStepUpRequiredException.class);

    MfaSession.markVerified(session, "doctor", t0);
    now.set(t0.plus(Duration.ofMinutes(4)));
    reauth.requireFreshMfa();
    now.set(t0.plus(Duration.ofMinutes(5)));
    assertThat(reauth.isFresh()).isTrue();
    now.set(t0.plus(Duration.ofMinutes(6)));
    assertThatThrownBy(reauth::requireFreshMfa).isInstanceOf(MfaStepUpRequiredException.class);

    // verification bound to another user does not count
    MfaSession.markVerified(session, "nurse", t0.plus(Duration.ofMinutes(6)));
    assertThat(reauth.isFresh()).isFalse();
  }

  @Test
  void noRequestContextMeansNotFresh() {
    assertThat(new MfaReauthService(() -> t0).isFresh()).isFalse();
  }

  @Test
  void redirectsStayLocal() {
    assertThat(SafeRedirect.localOrRoot("/prescriptions/1/sign")).isEqualTo("/prescriptions/1/sign");
    assertThat(SafeRedirect.localOrRoot("https://evil.example")).isEqualTo("/");
    assertThat(SafeRedirect.localOrRoot("//evil.example/x")).isEqualTo("/");
    assertThat(SafeRedirect.localOrRoot("/\\evil.example")).isEqualTo("/");
    assertThat(SafeRedirect.localOrRoot("/identity/mfa/step-up")).isEqualTo("/");
    assertThat(SafeRedirect.localOrRoot(null)).isEqualTo("/");
    assertThat(SafeRedirect.fromReferer("http://localhost/rx/9?x=1", "localhost")).isEqualTo("/rx/9?x=1");
    assertThat(SafeRedirect.fromReferer("http://evil.example/rx", "localhost")).isEqualTo("/");
  }
}
