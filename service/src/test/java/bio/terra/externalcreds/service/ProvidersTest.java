package bio.terra.externalcreds.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.externalcreds.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

public class ProvidersTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @Test
  void testListProviders() throws Exception {
    mvc.perform(get("/api/oidc/v1/providers")).andExpect(content().json("[\"fake-provider\"]"));
  }
}
