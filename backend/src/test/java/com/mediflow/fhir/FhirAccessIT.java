package com.mediflow.fhir;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.fhir.domain.FhirOAuthClient;
import com.mediflow.fhir.repo.FhirOAuthClientRepository;
import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.ConsentType;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.shared.repo.ConsentRepository;
import com.mediflow.shared.repo.PatientRepository;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class FhirAccessIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired PatientRepository patients;
  @Autowired AppUserRepository users;
  @Autowired ConsentRepository consents;
  @Autowired FhirOAuthClientRepository oauthClients;
  @Autowired PasswordEncoder passwordEncoder;

  @Test
  void metadataIsPublic() throws Exception {
    mvc.perform(get("/fhir/r4/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("CapabilityStatement"));
  }

  @Test
  void unauthenticatedPatientReadBlocked() throws Exception {
    Patient p = savePatient(null);
    mvc.perform(get("/fhir/r4/Patient/" + p.getId())).andExpect(status().isUnauthorized());
  }

  @Test
  void doctorWithoutAssignmentForbidden() throws Exception {
    Patient p = savePatient(null);
    mvc.perform(get("/fhir/r4/Patient/" + p.getId()).with(user("doc").roles("DOCTOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanReadPatient() throws Exception {
    Patient p = savePatient(null);
    mvc.perform(get("/fhir/r4/Patient/" + p.getId()).with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("Patient"))
        .andExpect(jsonPath("$.id").value(p.getId().toString()));
  }

  @Test
  void patientCanReadOwnRecord() throws Exception {
    AppUser u = saveUser("pat-" + UUID.randomUUID(), Role.ROLE_PATIENT);
    Patient p = savePatient(u.getId());
    mvc.perform(
            get("/fhir/r4/Patient/" + p.getId())
                .with(user(u.getUsername()).roles("PATIENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("Patient"));
  }

  @Test
  void clientCredentialsRequiresFhirShareConsent() throws Exception {
    Patient p = savePatient(null);
    String clientId = "fhir-client-" + UUID.randomUUID();
    String secret = "s3cret";
    FhirOAuthClient client = new FhirOAuthClient();
    client.setClientId(clientId);
    client.setClientSecretHash(passwordEncoder.encode(secret));
    client.setDisplayName("Test client");
    client.setActive(true);
    oauthClients.save(client);

    MvcResult tokenResult =
        mvc.perform(
                post("/fhir/r4/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
                    .param("client_id", clientId)
                    .param("client_secret", secret))
            .andExpect(status().isOk())
            .andReturn();
    String accessToken =
        com.jayway.jsonpath.JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.access_token");

    mvc.perform(
            get("/fhir/r4/Patient/" + p.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isForbidden());

    Consent consent = new Consent();
    consent.setPatientId(p.getId());
    consent.setConsentType(ConsentType.FHIR_SHARE);
    consent.setGranted(true);
    consent.setVersionLabel("t12-test");
    consent.setGrantedAt(Instant.now());
    consents.save(consent);

    mvc.perform(
            get("/fhir/r4/Patient/" + p.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("Patient"));

    mvc.perform(
            get("/fhir/r4/Patient/" + p.getId() + "/$everything")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("Bundle"));
  }

  private AppUser saveUser(String username, Role role) {
    AppUser u = new AppUser();
    u.setUsername(username);
    u.setPasswordHash("{noop}x");
    u.setDisplayName(username);
    u.setEnabled(true);
    u.setMfaEnabled(false);
    u.setLocale("en");
    u.setRoles(Set.of(role));
    return users.save(u);
  }

  private Patient savePatient(UUID userId) {
    Patient p = new Patient();
    p.setUserId(userId);
    p.setMrn("MRN-" + UUID.randomUUID().toString().substring(0, 8));
    p.setGivenName("Test");
    p.setFamilyName("Patient");
    p.setDateOfBirth(java.time.LocalDate.of(1995, 1, 1));
    p.setSex("F");
    return patients.save(p);
  }
}
