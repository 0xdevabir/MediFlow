package com.mediflow.identity.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import com.mediflow.identity.login.LoginAttemptService;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 5 wrong passwords lock the account for 15 minutes, even against the correct password. */
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class IdentityLockoutIT extends AbstractIntegrationTest {

  private static final String USER = "labtech";

  @Autowired MockMvc mvc;
  @Autowired LoginAttemptService attempts;
  @Autowired AppUserRepository users;

  @AfterEach
  void unlock() {
    attempts.unlock(userId());
  }

  @Test
  void fiveFailuresLockTheAccount() throws Exception {
    for (int i = 0; i < 4; i++) {
      mvc.perform(formLogin().user(USER).password("wrong-" + i)).andExpect(unauthenticated());
    }
    org.assertj.core.api.Assertions.assertThat(attempts.isLocked(userId())).isFalse();
    mvc.perform(formLogin().user(USER).password("wrong-4")).andExpect(unauthenticated());
    org.assertj.core.api.Assertions.assertThat(attempts.isLocked(userId())).isTrue();

    mvc.perform(formLogin().user(USER).password("password")).andExpect(unauthenticated());
  }

  @Test
  void successBeforeThresholdResetsCounter() throws Exception {
    for (int i = 0; i < 4; i++) {
      mvc.perform(formLogin().user(USER).password("wrong-" + i)).andExpect(unauthenticated());
    }
    mvc.perform(formLogin().user(USER).password("password")).andExpect(authenticated());
    mvc.perform(formLogin().user(USER).password("wrong-again")).andExpect(unauthenticated());
    org.assertj.core.api.Assertions.assertThat(attempts.isLocked(userId())).isFalse();
  }

  private UUID userId() {
    return users.findByUsername(USER).orElseThrow().getId();
  }
}
