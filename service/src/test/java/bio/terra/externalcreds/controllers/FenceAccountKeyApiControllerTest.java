package bio.terra.externalcreds.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.Provider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class FenceAccountKeyApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  private Provider provider = Provider.FENCE;

  @Nested
  class GetServiceAccountKey {

    @Test
    void testGetServiceAccountKeyIsDisabled() throws Exception {
      var accessToken = "testToken";

      mvc.perform(
              get("/api/fenceAccountKey/v1/{provider}", provider)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound())
          .andExpect(
              content().string(containsString("Fence service accounts no longer supported")));
    }
  }
}
