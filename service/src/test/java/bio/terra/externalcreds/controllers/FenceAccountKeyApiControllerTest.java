package bio.terra.externalcreds.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.LinkedAccountService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class FenceAccountKeyApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private LinkedAccountService linkedAccountServiceMock;

  @MockitoBean private ExternalCredsSamUserFactory samUserFactoryMock;
  private Provider provider = Provider.FENCE;

  @Nested
  class GetServiceAccountKey {

    @Test
    void testGetServiceAccountKeyIsDisabled() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var externalUserId = UUID.randomUUID().toString();

      mockSamUser(userId, accessToken);

      var linkedAccount =
          TestUtils.createRandomLinkedAccount(provider)
              .withExternalUserId(externalUserId)
              .withUserId(userId);

      when(linkedAccountServiceMock.getLinkedAccount(userId, provider))
          .thenReturn(Optional.of(linkedAccount));

      mvc.perform(
              get("/api/fenceAccountKey/v1/{provider}", provider)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound())
          .andExpect(
              content().string(containsString("Fence service accounts no longer supported")));
    }
  }

  private void mockSamUser(String userId, String accessToken) {
    when(samUserFactoryMock.from(any(HttpServletRequest.class)))
        .thenReturn(new SamUser("email", userId, new BearerToken(accessToken)));
  }
}
