package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.exception.NotFoundException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccount.Builder;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.PassportService;
import bio.terra.externalcreds.services.ProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

@AutoConfigureMockMvc
class OidcApiControllerTest extends BaseTest {

  @Autowired private ObjectMapper mapper;

  @Autowired private MockMvc mvc;

  @MockBean private LinkedAccountService linkedAccountServiceMock;
  @MockBean private ProviderService providerServiceMock;
  @MockBean private SamUserFactory samUserFactoryMock;
  @MockBean private PassportService passportServiceMock;
  @MockBean private AuditLogger auditLoggerMock;

  @Test
  void testListProviders() throws Exception {
    when(providerServiceMock.getProviderList())
        .thenReturn(Set.of("fake-provider2", "fake-provider1"));

    mvc.perform(get("/api/oidc/v1/providers"))
        .andExpect(content().json("""
            ["fake-provider1","fake-provider2"]"""));
  }

  @Nested
  class GetAuthUrl {

    @Test
    void testGetAuthUrl() throws Exception {
      var userId = "fakeUser";
      var accessToken = "fakeAccessToken";
      var result = "https://test/authorization/uri";
      var providerName = "fake";
      var redirectUri = "fakeuri";

      mockSamUser(userId, accessToken);

      when(providerServiceMock.getProviderAuthorizationUrl(userId, providerName, redirectUri))
          .thenReturn(Optional.of(result));

      var queryParams = new LinkedMultiValueMap<String, String>();
      queryParams.add("redirectUri", redirectUri);
      mvc.perform(
              get("/api/oidc/v1/{provider}/authorization-url", providerName)
                  .header("authorization", "Bearer " + accessToken)
                  .queryParams(queryParams))
          .andExpect(content().json("\"" + result + "\""));
    }

    @Test
    void testGetAuthUrl404() throws Exception {
      var userId = "fakeUser";
      var accessToken = "fakeAccessToken";
      var providerName = "fake";
      var redirectUri = "fakeuri";

      mockSamUser(userId, accessToken);

      when(providerServiceMock.getProviderAuthorizationUrl(userId, providerName, redirectUri))
          .thenReturn(Optional.empty());

      var queryParams = new LinkedMultiValueMap<String, String>();
      queryParams.add("redirectUri", redirectUri);
      mvc.perform(
              get("/api/oidc/v1/{provider}/authorization-url", providerName)
                  .header("authorization", "Bearer " + accessToken)
                  .queryParams(queryParams))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class GetLink {

    @Test
    void testGetLink() throws Exception {
      var accessToken = "testToken";
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount();

      mockSamUser(inputLinkedAccount.getUserId(), accessToken);

      when(linkedAccountServiceMock.getLinkedAccount(
              inputLinkedAccount.getUserId(), inputLinkedAccount.getProviderName()))
          .thenReturn(Optional.of(inputLinkedAccount));

      mvc.perform(
              get("/api/oidc/v1/" + inputLinkedAccount.getProviderName())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          OpenApiConverters.Output.convert(inputLinkedAccount))));
    }

    @Test
    void testGetLink404() throws Exception {
      var userId = "non-existent-user";
      var providerName = "non-existent-provider";
      var accessToken = "testToken";

      mockSamUser(userId, accessToken);

      mvc.perform(
              get("/api/oidc/v1/" + providerName).header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class CreateLink {

    @Test
    void testCreatesLinkSuccessfully() throws Exception {
      var accessToken = "testToken";
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount();

      var state = UUID.randomUUID().toString();
      var oauthcode = UUID.randomUUID().toString();

      mockSamUser(inputLinkedAccount.getUserId(), accessToken);

      when(providerServiceMock.createLink(
              inputLinkedAccount.getProviderName(),
              inputLinkedAccount.getUserId(),
              oauthcode,
              state))
          .thenReturn(
              Optional.of(
                  new LinkedAccountWithPassportAndVisas.Builder()
                      .linkedAccount(inputLinkedAccount)
                      .build()));

      mvc.perform(
              post("/api/oidc/v1/{provider}/oauthcode", inputLinkedAccount.getProviderName())
                  .header("authorization", "Bearer " + accessToken)
                  .param("state", state)
                  .param("oauthcode", oauthcode))
          .andExpect(status().isOk())
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          OpenApiConverters.Output.convert(inputLinkedAccount))));

      // check that a log was recorded
      verify(auditLoggerMock)
          .logEvent(
              new AuditLogEvent.Builder()
                  .auditLogEventType(AuditLogEventType.LinkCreated)
                  .providerName(inputLinkedAccount.getProviderName())
                  .userId(inputLinkedAccount.getUserId())
                  .clientIP("127.0.0.1")
                  .externalUserId(inputLinkedAccount.getExternalUserId())
                  .build());
    }

    @Test
    void testExceptionIsLogged() throws Exception {
      var accessToken = "testToken";

      var userId = "userId";
      mockSamUser(userId, accessToken);

      when(providerServiceMock.createLink(any(), any(), any(), any()))
          .thenThrow(new ExternalCredsException("This is a drill!"));

      // check that an internal server error code is returned
      var testProviderName = "testProviderName";
      mvc.perform(
              post("/api/oidc/v1/{provider}/oauthcode", testProviderName)
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
                  .providerName(testProviderName)
                  .userId(userId)
                  .clientIP("127.0.0.1")
                  .build());
    }
  }

  @Nested
  class DeleteLink {

    @Test
    void testDeleteLink() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var providerName = UUID.randomUUID().toString();
      var externalId = UUID.randomUUID().toString();
      mockSamUser(userId, accessToken);

      when(providerServiceMock.deleteLink(userId, providerName))
          .thenReturn(
              new Builder()
                  .providerName(providerName)
                  .userId(userId)
                  .externalUserId(externalId)
                  .expires(new Timestamp(0))
                  .isAuthenticated(true)
                  .refreshToken("")
                  .build());

      mvc.perform(
              delete("/api/oidc/v1/{provider}", providerName)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk());

      verify(providerServiceMock).deleteLink(userId, providerName);

      // check that a log was recorded
      verify(auditLoggerMock)
          .logEvent(
              new AuditLogEvent.Builder()
                  .auditLogEventType(AuditLogEventType.LinkDeleted)
                  .providerName(providerName)
                  .userId(userId)
                  .clientIP("127.0.0.1")
                  .externalUserId(externalId)
                  .build());
    }

    @Test
    void testDeleteLink404() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var providerName = UUID.randomUUID().toString();
      mockSamUser(userId, accessToken);

      doThrow(new NotFoundException("not found"))
          .when(providerServiceMock)
          .deleteLink(userId, providerName);

      mvc.perform(
              delete("/api/oidc/v1/{provider}", providerName)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class GetProviderPassport {

    @Test
    void testGetProviderPassport() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var providerName = UUID.randomUUID().toString();
      var externalUserId = UUID.randomUUID().toString();
      var passport =
          TestUtils.createRandomPassport()
              .withExpires(new Timestamp(System.currentTimeMillis() + 1000));

      mockSamUser(userId, accessToken);

      when(linkedAccountServiceMock.getLinkedAccount(userId, providerName))
          .thenReturn(
              Optional.of(
                  new LinkedAccount.Builder()
                      .providerName(providerName)
                      .userId(userId)
                      .externalUserId(externalUserId)
                      .refreshToken("")
                      .expires(new Timestamp(0))
                      .isAuthenticated(true)
                      .build()));
      when(passportServiceMock.getPassport(userId, providerName)).thenReturn(Optional.of(passport));

      mvc.perform(
              get("/api/oidc/v1/{provider}/passport", providerName)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk())
          .andExpect(content().json("\"" + passport.getJwt() + "\""));

      // check that a log was recorded
      verify(auditLoggerMock)
          .logEvent(
              new AuditLogEvent.Builder()
                  .auditLogEventType(AuditLogEventType.GetPassport)
                  .providerName(providerName)
                  .userId(userId)
                  .clientIP("127.0.0.1")
                  .externalUserId(externalUserId)
                  .build());
    }

    @Test
    void testGetProviderPassportDoesNotReturnExpired() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var providerName = UUID.randomUUID().toString();
      var passport =
          TestUtils.createRandomPassport()
              .withExpires(new Timestamp(System.currentTimeMillis() - 1000));

      mockSamUser(userId, accessToken);

      when(passportServiceMock.getPassport(userId, providerName)).thenReturn(Optional.of(passport));

      mvc.perform(
              get("/api/oidc/v1/{provider}/passport", providerName)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }

    @Test
    void testGetProviderPassport404() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var providerName = UUID.randomUUID().toString();

      mockSamUser(userId, accessToken);

      mvc.perform(
              get("/api/oidc/v1/{provider}/passport", providerName)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }
  }

  private void mockSamUser(String userId, String accessToken) {
    when(samUserFactoryMock.from(any(HttpServletRequest.class), any(String.class)))
        .thenReturn(new SamUser("email", userId, new BearerToken(accessToken)));
  }
}
