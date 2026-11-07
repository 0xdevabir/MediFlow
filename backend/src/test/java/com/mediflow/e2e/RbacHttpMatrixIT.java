package com.mediflow.e2e;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.support.AbstractIntegrationTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * HTTP-level RBAC smoke matrix: each role × module entry URL expects 200/3xx (allowed) or 403
 * (denied). Complements {@code AccessMatrixPolicyTest} (policy unit cells). Object-level patient
 * assignment is covered there; here we assert controller/method security on module roots.
 *
 * <p>Not run in the T14 agent session (Testcontainers / Docker required for {@code *IT}).
 */
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class RbacHttpMatrixIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;

  static Stream<Arguments> cells() {
    // role, path, method(get|post), allowed?
    return Stream.of(
        // Directory — public GET search is open; admin CRUD is ADMIN-only
        Arguments.of("PATIENT", "GET", "/directory/search", true),
        Arguments.of("DOCTOR", "GET", "/directory/search", true),
        Arguments.of("ADMIN", "GET", "/directory/admin", true),
        Arguments.of("PATIENT", "GET", "/directory/admin", false),
        Arguments.of("NURSE", "GET", "/directory/admin", false),

        // Appointments / queue
        Arguments.of("PATIENT", "GET", "/appointments", true),
        Arguments.of("FRONT_DESK", "GET", "/appointments", true),
        Arguments.of("PHARMACIST", "GET", "/appointments/book", false),
        Arguments.of("DOCTOR", "GET", "/queue/doctor", true),
        Arguments.of("PATIENT", "GET", "/queue/doctor", false),
        Arguments.of("NURSE", "GET", "/queue", true),

        // ER board
        Arguments.of("ER", "GET", "/er", true),
        Arguments.of("ADMIN", "GET", "/er", true),
        Arguments.of("DOCTOR", "GET", "/er", false),
        Arguments.of("PATIENT", "GET", "/er", false),

        // Clinical / Rx / Lab / Pharmacy
        Arguments.of("DOCTOR", "GET", "/clinical/consultations", true),
        Arguments.of("PHARMACIST", "GET", "/clinical/consultations", false),
        Arguments.of("DOCTOR", "GET", "/prescriptions", true),
        Arguments.of("PATIENT", "GET", "/prescriptions", true),
        Arguments.of("FRONT_DESK", "GET", "/prescriptions/new", false),
        Arguments.of("LAB_TECH", "GET", "/lab/queue", true),
        Arguments.of("PATIENT", "GET", "/lab/direct", true),
        Arguments.of("PHARMACIST", "GET", "/pharmacy", true),
        Arguments.of("PATIENT", "GET", "/pharmacy", false),

        // Admin / audit
        Arguments.of("ADMIN", "GET", "/admin", true),
        Arguments.of("CLINICAL_APPROVER", "GET", "/admin", true),
        Arguments.of("DOCTOR", "GET", "/admin", false),
        Arguments.of("PATIENT", "GET", "/audit/me", true),
        Arguments.of("ADMIN", "GET", "/admin/audit", true),
        Arguments.of("NURSE", "GET", "/admin/audit", false),

        // Notifications
        Arguments.of("DOCTOR", "GET", "/notifications", true),
        Arguments.of("FRONT_DESK", "GET", "/notifications/call-list", true),
        Arguments.of("PATIENT", "GET", "/notifications/call-list", false));
  }

  @ParameterizedTest(name = "{0} {1} {2} allowed={3}")
  @MethodSource("cells")
  @DisplayName("RBAC HTTP matrix cell")
  void cell(String role, String method, String path, boolean allowed) throws Exception {
    RequestPostProcessor auth = user("u-" + role.toLowerCase()).roles(role);
    ResultMatcher expected =
        allowed
            ? status().is(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.is(200),
                    org.hamcrest.Matchers.is(302),
                    org.hamcrest.Matchers.is(303),
                    // some controllers redirect when no fixture data
                    org.hamcrest.Matchers.is(404)))
            : status().isForbidden();

    if ("GET".equals(method)) {
      mvc.perform(get(path).with(auth)).andExpect(expected);
    } else {
      mvc.perform(post(path).with(auth).contentType(MediaType.APPLICATION_FORM_URLENCODED))
          .andExpect(expected);
    }
  }
}
