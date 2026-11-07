package com.mediflow.identity.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.shared.domain.AppUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class MfaGateFilterTest {

  private final MfaService mfa = mock(MfaService.class);
  private final UUID doctorId = UUID.randomUUID();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private MfaGateFilter filter(MockEnvironment env) {
    return new MfaGateFilter(new MfaEnforcement(env), new MfaRequirementResolver(mfa));
  }

  private MfaGateFilter enforcing() {
    return filter(new MockEnvironment().withProperty(MfaEnforcement.PROPERTY, "true"));
  }

  private void signIn(String name, String role, boolean optedIn, boolean enrolled) {
    AppUser u = new AppUser();
    u.setId(doctorId);
    u.setUsername(name);
    u.setMfaEnabled(optedIn);
    when(mfa.user(name)).thenReturn(Optional.of(u));
    when(mfa.hasVerifiedSecret(doctorId)).thenReturn(enrolled);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                name, null, AuthorityUtils.createAuthorityList(role)));
  }

  private MockHttpServletResponse run(MfaGateFilter f, MockHttpServletRequest req) throws Exception {
    MockHttpServletResponse res = new MockHttpServletResponse();
    f.doFilter(req, res, new MockFilterChain());
    return res;
  }

  private static MockHttpServletRequest get(String uri, MockHttpSession session) {
    MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
    r.setSession(session);
    return r;
  }

  @Test
  void doctorWithoutSecretIsSentToEnrolment() throws Exception {
    signIn("doctor", "ROLE_DOCTOR", false, false);
    assertThat(run(enforcing(), get("/", new MockHttpSession())).getRedirectedUrl())
        .isEqualTo("/identity/mfa/enrol");
  }

  @Test
  void enrolledPharmacistMustVerifyThenPasses() throws Exception {
    signIn("pharmacist", "ROLE_PHARMACIST", true, true);
    MockHttpSession session = new MockHttpSession();
    assertThat(run(enforcing(), get("/pharmacy", session)).getRedirectedUrl())
        .isEqualTo("/identity/mfa/verify");
    MfaSession.markVerified(session, "pharmacist", Instant.now());
    assertThat(run(enforcing(), get("/pharmacy", session)).getRedirectedUrl()).isNull();
  }

  @Test
  void requiredForAdminAndApprover() throws Exception {
    for (String role : new String[] {"ROLE_ADMIN", "ROLE_CLINICAL_APPROVER"}) {
      signIn("u", role, false, true);
      assertThat(run(enforcing(), get("/admin", new MockHttpSession())).getRedirectedUrl())
          .isEqualTo("/identity/mfa/verify");
    }
  }

  @Test
  void nurseWithoutOptInPasses() throws Exception {
    signIn("nurse", "ROLE_NURSE", false, false);
    assertThat(run(enforcing(), get("/queue", new MockHttpSession())).getRedirectedUrl()).isNull();
  }

  @Test
  void optedInPatientMustVerify() throws Exception {
    signIn("patient", "ROLE_PATIENT", true, true);
    assertThat(run(enforcing(), get("/", new MockHttpSession())).getRedirectedUrl())
        .isEqualTo("/identity/mfa/verify");
  }

  @Test
  void apiGets401AndMfaPagesAreOpen() throws Exception {
    signIn("doctor", "ROLE_DOCTOR", false, true);
    assertThat(run(enforcing(), get("/api/v1/x", new MockHttpSession())).getStatus()).isEqualTo(401);
    assertThat(run(enforcing(), get("/identity/mfa/verify", new MockHttpSession())).getRedirectedUrl())
        .isNull();
    assertThat(run(enforcing(), get("/webjars/x.css", new MockHttpSession())).getRedirectedUrl())
        .isNull();
  }

  @Test
  void verificationForAnotherUserDoesNotCount() throws Exception {
    signIn("doctor", "ROLE_DOCTOR", false, true);
    MockHttpSession session = new MockHttpSession();
    MfaSession.markVerified(session, "nurse", Instant.now());
    assertThat(run(enforcing(), get("/", session)).getRedirectedUrl()).isEqualTo("/identity/mfa/verify");
  }

  @Test
  void enforcementDefaults() {
    MockEnvironment dev = new MockEnvironment();
    dev.setActiveProfiles("test", "dev");
    assertThat(new MfaEnforcement(dev).enforced()).isFalse();
    assertThat(new MfaEnforcement(new MockEnvironment()).enforced()).isTrue();
    MockEnvironment devOn = new MockEnvironment().withProperty(MfaEnforcement.PROPERTY, "true");
    devOn.setActiveProfiles("dev");
    assertThat(new MfaEnforcement(devOn).enforced()).isTrue();
  }

  @Test
  void notEnforcedPassesEverything() throws Exception {
    MockEnvironment dev = new MockEnvironment();
    dev.setActiveProfiles("dev");
    signIn("doctor", "ROLE_DOCTOR", false, false);
    assertThat(run(filter(dev), get("/", new MockHttpSession())).getRedirectedUrl()).isNull();
  }
}
