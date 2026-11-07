package com.mediflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.identity.mfa.IdentityMfaSecretRepository;
import com.mediflow.identity.mfa.MfaSession;
import com.mediflow.identity.mfa.TotpService;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/** MFA gate with enforcement on: enrol (QR) on first login, verify on later logins, patient exempt. */
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = "mediflow.identity.mfa.enforce=true")
class IdentityMfaIT extends AbstractIntegrationTest {

  private static final String USER = "pharmacist";

  @Autowired MockMvc mvc;
  @Autowired TotpService totp;
  @Autowired IdentityMfaSecretRepository secrets;
  @Autowired AppUserRepository users;
  @Autowired TransactionTemplate tx;

  @AfterEach
  void clearSecret() {
    tx.executeWithoutResult(
        s -> secrets.deleteByUserId(users.findByUsername(USER).orElseThrow().getId()));
  }

  @Test
  void pharmacistMustEnrolThenVerify() throws Exception {
    MockHttpSession session = login(USER);
    mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/identity/mfa/enrol"));
    mvc.perform(get("/api/anything").session(session)).andExpect(status().isUnauthorized());

    MvcResult enrol = mvc.perform(get("/identity/mfa/enrol").session(session)).andExpect(status().isOk()).andReturn();
    String secret = (String) enrol.getModelAndView().getModel().get("secret");
    assertThat((String) enrol.getModelAndView().getModel().get("qr")).startsWith("data:image/png;base64,");

    mvc.perform(post("/identity/mfa/enrol").session(session).with(csrf()).param("code", "000000"))
        .andExpect(redirectedUrl("/identity/mfa/enrol"));
    long step = TotpService.step(Instant.now());
    mvc.perform(post("/identity/mfa/enrol").session(session).with(csrf()).param("code", totp.codeAt(secret, step)))
        .andExpect(redirectedUrl("/"));
    assertThat(session.getAttribute(MfaSession.VERIFIED_AT)).isNotNull();
    mvc.perform(get("/").session(session)).andExpect(status().isOk());

    // Next login: verify, not enrol; the same code (same step) is rejected as a replay.
    MockHttpSession second = login(USER);
    mvc.perform(get("/").session(second)).andExpect(redirectedUrl("/identity/mfa/verify"));
    mvc.perform(post("/identity/mfa/verify").session(second).with(csrf()).param("code", totp.codeAt(secret, step)))
        .andExpect(redirectedUrl("/identity/mfa/verify"));
    mvc.perform(post("/identity/mfa/verify").session(second).with(csrf()).param("code", totp.codeAt(secret, step + 1)))
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void patientIsNotGated() throws Exception {
    mvc.perform(get("/").session(login("patient"))).andExpect(status().isOk());
  }

  private MockHttpSession login(String username) throws Exception {
    MvcResult r = mvc.perform(formLogin().user(username).password("password")).andReturn();
    return (MockHttpSession) r.getRequest().getSession(false);
  }
}
