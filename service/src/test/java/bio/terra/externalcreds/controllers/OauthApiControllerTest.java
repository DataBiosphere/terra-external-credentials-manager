package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.ProviderService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

@AutoConfigureMockMvc
class OauthApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean
  @Qualifier("providerService")
  private ProviderService providerServiceMock;

  @MockBean private SamUserFactory samUserFactoryMock;
  private String providerName = Provider.RAS.toString();

  @Nested
  class GetAuthUrl {

    @Test
    void testGetAuthUrl() throws Exception {
      var userId = "fakeUser";
      var accessToken = "fakeAccessToken";
      var result = "https://test/authorization/uri";
      var redirectUri = "fakeuri";

      mockSamUser(userId, accessToken);

      when(providerServiceMock.getProviderAuthorizationUrl(userId, providerName, redirectUri))
          .thenReturn(Optional.of(result));

      var queryParams = new LinkedMultiValueMap<String, String>();
      queryParams.add("redirectUri", redirectUri);
      mvc.perform(
              get("/api/oauth/v1/{provider}/authorization-url", providerName)
                  .header("authorization", "Bearer " + accessToken)
                  .queryParams(queryParams))
          .andExpect(content().string(result));
    }

    @Test
    void testGetAuthUrl404() throws Exception {
      var userId = "fakeUser";
      var accessToken = "fakeAccessToken";
      var redirectUri = "fakeuri";

      mockSamUser(userId, accessToken);

      when(providerServiceMock.getProviderAuthorizationUrl(userId, providerName, redirectUri))
          .thenReturn(Optional.empty());

      var queryParams = new LinkedMultiValueMap<String, String>();
      queryParams.add("redirectUri", redirectUri);
      mvc.perform(
              get("/api/oauth/v1/{provider}/authorization-url", providerName)
                  .header("authorization", "Bearer " + accessToken)
                  .queryParams(queryParams))
          .andExpect(status().isNotFound());
    }
  }

  private void mockSamUser(String userId, String accessToken) {
    when(samUserFactoryMock.from(any(HttpServletRequest.class), any(String.class)))
        .thenReturn(new SamUser("email", userId, new BearerToken(accessToken)));
  }
}
