package com.mediflow.directory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mediflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Public search is anonymous; admin CRUD requires ROLE_ADMIN. Tests were written but not run in
 * this agent session (no Docker / no {@code mvnw verify}).
 */
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class DirectorySearchIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;

  @Test
  void searchIsPublic() throws Exception {
    mvc.perform(get("/directory/search"))
        .andExpect(status().isOk())
        .andExpect(view().name("directory/search"));
  }

  @Test
  void adminIndexRequiresAdmin() throws Exception {
    mvc.perform(get("/directory/admin")).andExpect(status().is3xxRedirection());
    mvc.perform(get("/directory/admin").with(user("doctor").roles("DOCTOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "admin", roles = "ADMIN")
  void adminIndexOkForAdmin() throws Exception {
    mvc.perform(get("/directory/admin"))
        .andExpect(status().isOk())
        .andExpect(view().name("directory/admin/index"));
  }
}
