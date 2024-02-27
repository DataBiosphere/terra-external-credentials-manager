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
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.services.PassportProviderService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.TokenProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
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
  @Autowired private ObjectMapper mapper;

  @Autowired private MockMvc mvc;

  @MockBean
  @Qualifier("providerService")
  private ProviderService providerServiceMock;

  @MockBean
  @Qualifier("passportProviderService")
  private PassportProviderService passportProviderServiceMock;

  @MockBean
  @Qualifier("tokenProviderService")
  private TokenProviderService tokenProviderServiceMock;

  @MockBean private SamUserFactory samUserFactoryMock;
  @MockBean private AuditLogger auditLoggerMock;

  private String providerName = Provider.RAS.toString();

  @Nested
  class GetProviderAccessToken {

    @Test
    void testGetProviderAccessToken() throws Exception {
      var userId = "fakeUser";
      var accessToken = "fakeAccessToken";
      var githubAccessToken = "fakeGithubAccessToken";
      var provider = Provider.GITHUB;
      mockSamUser(userId, accessToken);

      when(tokenProviderServiceMock.getProviderAccessToken(any(), eq(provider), any()))
          .thenReturn(Optional.of(githubAccessToken));

      mvc.perform(
              get("/api/oidc/v1/{provider}/access-token", provider.toString())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk())
          .andExpect(content().string(githubAccessToken));
    }

    @Test
    void testGetProviderAccessToken404() throws Exception {
      var userId = "fakeUser";
      var accessToken = "fakeAccessToken";
      var provider = Provider.GITHUB;
      mockSamUser(userId, accessToken);

      when(tokenProviderServiceMock.getProviderAccessToken(any(), eq(provider), any()))
          .thenReturn(Optional.empty());

      mvc.perform(
              get("/api/oidc/v1/{provider}/access-token", provider.toString())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }
  }

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

  @Nested
  class CreateLink {

    @Test
    void testCreatesTokenProviderLinkSuccessfully() throws Exception {
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.GITHUB.toString());

      var state = UUID.randomUUID().toString();
      var oauthcode = UUID.randomUUID().toString();

      when(tokenProviderServiceMock.createLink(
              eq(inputLinkedAccount.getProviderName()),
              eq(inputLinkedAccount.getUserId()),
              eq(oauthcode),
              eq(state),
              any(AuditLogEvent.Builder.class)))
          .thenReturn(Optional.of(inputLinkedAccount));
      testCreatesLinkSuccessfully(inputLinkedAccount, state, oauthcode);
    }

    @Test
    void testCreatesPassportProviderLinkSuccessfully() throws Exception {
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount();
      var state = UUID.randomUUID().toString();
      var oauthcode = UUID.randomUUID().toString();

      var linkedAccountWithPassportAndVisas =
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(inputLinkedAccount)
              .passport(TestUtils.createRandomPassport())
              .build();
      when(passportProviderServiceMock.createLink(
              eq(inputLinkedAccount.getProviderName()),
              eq(inputLinkedAccount.getUserId()),
              eq(oauthcode),
              eq(state),
              any(AuditLogEvent.Builder.class)))
          .thenReturn(Optional.of(linkedAccountWithPassportAndVisas));

      testCreatesLinkSuccessfully(inputLinkedAccount, state, oauthcode);
    }

    @Test
    void testExceptionIsLogged() throws Exception {
      var accessToken = "testToken";

      var userId = "userId";
      mockSamUser(userId, accessToken);

      when(passportProviderServiceMock.createLink(any(), any(), any(), any(), any()))
          .thenThrow(new ExternalCredsException("This is a drill!"));

      // check that an internal server error code is returned
      mvc.perform(
              post("/api/oauth/v1/{provider}/oauthcode", providerName)
                  .header("authorization", "Bearer " + accessToken)
                  .param("scopes", "foo")
                  .param("redirectUri", "redirectUri")
                  .param("state", "state")
                  .param("oauthcode", "oauthcode"))
          .andExpect(status().isInternalServerError());

      // check that a log was recorded
      verify(auditLoggerMock)
          .logEvent(
              new AuditLogEvent.Builder()
                  .auditLogEventType(AuditLogEventType.LinkCreationFailed)
                  .providerName(providerName)
                  .userId(userId)
                  .clientIP("127.0.0.1")
                  .build());
    }
  }

  private void testCreatesLinkSuccessfully(
      LinkedAccount inputLinkedAccount, String state, String oauthcode) throws Exception {
    var accessToken = "testToken";
    mockSamUser(inputLinkedAccount.getUserId(), accessToken);
    mvc.perform(
            post("/api/oauth/v1/{provider}/oauthcode", inputLinkedAccount.getProviderName())
                .header("authorization", "Bearer " + accessToken)
                .param("state", state)
                .param("oauthcode", oauthcode))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    mapper.writeValueAsString(
                        OpenApiConverters.Output.convert(inputLinkedAccount))));
  }

  private void mockSamUser(String userId, String accessToken) {
    when(samUserFactoryMock.from(any(HttpServletRequest.class), any(String.class)))
        .thenReturn(new SamUser("email", userId, new BearerToken(accessToken)));
  }
}
