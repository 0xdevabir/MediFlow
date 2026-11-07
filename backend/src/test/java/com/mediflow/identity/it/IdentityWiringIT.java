package com.mediflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.identity.access.AccessPolicy;
import com.mediflow.identity.access.IdentityPatientAccessPort;
import com.mediflow.shared.port.PatientAccessPort;
import com.mediflow.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** T01 beans replace the T00 stubs; public password pages are reachable anonymously. */
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class IdentityWiringIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;
  @Autowired PatientAccessPort patientAccess;

  @Test
  void realPatientAccessPortAndAccessBeanAreWired() {
    assertThat(patientAccess).isInstanceOf(IdentityPatientAccessPort.class);
    assertThat(context.getBean("access")).isInstanceOf(AccessPolicy.class);
  }

  @Test
  @WithMockUser(username = "doctor", roles = "DOCTOR")
  void unknownPatientIsDeniedByDefault() {
    AccessPolicy access = context.getBean("access", AccessPolicy.class);
    UUID random = UUID.randomUUID();
    assertThat(access.canReadPatient(random)).isFalse();
    assertThat(access.canWritePatient(random)).isFalse();
    assertThat(access.canReadLabOrder(random)).isFalse();
    assertThat(access.canReadPrescription(random)).isFalse();
  }

  @Test
  void passwordPagesArePublic() throws Exception {
    mvc.perform(get("/identity/password/forgot"))
        .andExpect(status().isOk())
        .andExpect(header().string("Referrer-Policy", "no-referrer"));
    mvc.perform(get("/identity/password/reset").param("token", "not-a-real-token")).andExpect(status().isOk());
  }

  @Test
  void breakGlassReviewIsAdminOnly() throws Exception {
    mvc.perform(get("/identity/break-glass/review")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("doctor").roles("DOCTOR")))
        .andExpect(status().isForbidden());
  }
}
