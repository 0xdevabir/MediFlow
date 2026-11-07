package com.mediflow.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Uses the real dev seed users ({@code DevDataLoader}) and the real form-login chain. */
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class LoginAndNavigationIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired CurrentUser currentUser;

  @Test
  @org.springframework.security.test.context.support.WithMockUser(username = "doctor", roles = "DOCTOR")
  void currentUserResolvesSeededAppUserId() {
    assertThat(currentUser.username()).contains("doctor");
    assertThat(currentUser.userId()).isPresent();
    assertThat(currentUser.hasRole("DOCTOR")).isTrue();
    assertThat(currentUser.hasRole("ADMIN")).isFalse();
  }

  @Test
  void anonymousIsRedirectedToLogin() throws Exception {
    mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    mvc.perform(get("/patients")).andExpect(status().is3xxRedirection());
  }

  @Test
  void loginPageRendersWithCsrfAndSecurityHeaders() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")));
  }

  @Test
  void wrongPasswordIsRejected() throws Exception {
    mvc.perform(formLogin().user("doctor").password("nope"))
        .andExpect(unauthenticated())
        .andExpect(redirectedUrl("/login?error"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "patient", "doctor", "nurse", "frontdesk", "labtech", "pharmacist", "er", "admin", "approver"
      })
  void everySeededRoleCanLogIn(String username) throws Exception {
    mvc.perform(formLogin().user(username).password("password"))
        .andExpect(authenticated().withUsername(username))
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void doctorNavShowsClinicalLinksOnly() throws Exception {
    String html = homeAs("doctor");
    assertThat(html)
        .contains("href=\"/consultations\"", "href=\"/prescriptions\"", "href=\"/queue\"", "href=\"/audit/me\"")
        .doesNotContain("href=\"/pharmacy\"", "href=\"/admin\"", "href=\"/patients\"");
  }

  @Test
  void pharmacistNavShowsPharmacyNotConsultations() throws Exception {
    String html = homeAs("pharmacist");
    assertThat(html)
        .contains("href=\"/pharmacy\"", "href=\"/prescriptions\"")
        .doesNotContain("href=\"/consultations\"", "href=\"/admin\"", "href=\"/queue\"");
  }

  @Test
  void patientNavHasNoStaffLinks() throws Exception {
    String html = homeAs("patient");
    assertThat(html)
        .contains("href=\"/appointments\"", "href=\"/lab\"", "href=\"/audit/me\"")
        .doesNotContain(
            "href=\"/queue\"", "href=\"/consultations\"", "href=\"/pharmacy\"", "href=\"/admin\"");
  }

  @Test
  void adminNavShowsAdmin() throws Exception {
    assertThat(homeAs("admin")).contains("href=\"/admin\"", "href=\"/patients\"");
  }

  @Test
  void placeholderModulePagesRender() throws Exception {
    MockHttpSession session = login("admin");
    for (String path :
        new String[] {
          "/patients", "/directory", "/appointments", "/queue", "/consultations",
          "/prescriptions", "/lab", "/pharmacy", "/admin", "/audit/me"
        }) {
      mvc.perform(get(path).session(session))
          .andExpect(status().isOk())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("Coming soon")));
    }
  }

  @Test
  void languageSwitcherRendersBangla() throws Exception {
    mvc.perform(get("/").param("lang", "bn").session(login("doctor")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("মেডিফ্লো")));
  }

  @Test
  void flashMessagesRenderFromKeyOrText() throws Exception {
    mvc.perform(get("/").session(login("doctor")).flashAttr("successKey", "login.logout"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("You have been signed out.")));
    mvc.perform(get("/").session(login("doctor")).flashAttr("errorMessage", "Boom text"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Boom text")));
  }

  @Test
  void unknownPathReturns404Page() throws Exception {
    mvc.perform(get("/no-such-page").session(login("doctor"))).andExpect(status().isNotFound());
  }

  private String homeAs(String username) throws Exception {
    return mvc.perform(get("/").session(login(username)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private MockHttpSession login(String username) throws Exception {
    MockHttpSession session =
        (MockHttpSession)
            mvc.perform(formLogin().user(username).password("password"))
                .andExpect(authenticated())
                .andReturn()
                .getRequest()
                .getSession(false);
    assertThat(session).isNotNull();
    return session;
  }
}
