package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.PassportService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

@AutoConfigureMockMvc
public class OidcApiControllerTest extends BaseTest {

  @Autowired private ObjectMapper mapper;

  @Autowired private MockMvc mvc;
  @Autowired private OidcApiController oidcApiController;

  @MockBean private LinkedAccountService linkedAccountServiceMock;
  @MockBean private ProviderService providerServiceMock;
  @MockBean private SamService samServiceMock;
  @MockBean private PassportService passportServiceMock;
  @MockBean private AuditLogger auditLoggerMock;

  @Test
  void testListProviders() throws Exception {
    when(providerServiceMock.getProviderList())
        .thenReturn(Set.of("fake-provider2", "fake-provider1"));

    mvc.perform(get("/api/oidc/v1/providers"))
        .andExpect(content().json("[\"fake-provider1\",\"fake-provider2\"]"));
  }

  @Nested
  class GetAuthUrl {

    @Test
    void testGetAuthUrl() throws Exception {
      var result = "https://test/authorization/uri";
      var providerName = "fake";
      var redirectUri = "fakeuri";
      var scopes = Set.of("openid", "email");
      String state = null;

      when(providerServiceMock.getProviderAuthorizationUrl(
              providerName, redirectUri, scopes, state))
          .thenReturn(Optional.of(result));

      var queryParams = new LinkedMultiValueMap<String, String>();
      queryParams.add("redirectUri", redirectUri);
      queryParams.addAll("scopes", List.copyOf(scopes));
      mvc.perform(
              get("/api/oidc/v1/{provider}/authorization-url", providerName)
                  .queryParams(queryParams))
          .andExpect(content().json("\"" + result + "\""));
    }

    @Test
    void testGetAuthUrl404() throws Exception {
      var providerName = "fake";
      var redirectUri = "fakeuri";
      var scopes = Set.of("openid", "email");
      String state = null;

      when(providerServiceMock.getProviderAuthorizationUrl(
              providerName, redirectUri, scopes, state))
          .thenReturn(Optional.empty());

      var queryParams = new LinkedMultiValueMap<String, String>();
      queryParams.add("redirectUri", redirectUri);
      queryParams.addAll("scopes", List.copyOf(scopes));
      mvc.perform(
              get("/api/oidc/v1/{provider}/authorization-url", providerName)
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
              eq(inputLinkedAccount.getUserId()), eq(inputLinkedAccount.getProviderName())))
          .thenReturn(Optional.of(inputLinkedAccount));

      mvc.perform(
              get("/api/oidc/v1/" + inputLinkedAccount.getProviderName())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          oidcApiController.getLinkInfoFromLinkedAccount(inputLinkedAccount))));
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

    @Test
    void testGetLink403() throws Exception {
      var providerName = "provider";
      var accessToken = "testToken";

      mockSamUserError(accessToken, HttpStatus.NOT_FOUND);

      mvc.perform(
              get("/api/oidc/v1/" + providerName).header("authorization", "Bearer " + accessToken))
          .andExpect(status().isForbidden());
    }

    @Test
    void testGetLink500() throws Exception {
      String providerName = "provider";
      String accessToken = "testToken";

      mockSamUserError(accessToken, HttpStatus.NO_CONTENT);

      mvc.perform(
              get("/api/oidc/v1/" + providerName).header("authorization", "Bearer " + accessToken))
          .andExpect(status().isInternalServerError());
    }
  }

  @Nested
  class CreateLink {

    @Test
    void testCreatesLinkSuccessfully() throws Exception {
      var accessToken = "testToken";
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount();

      var scopes = new String[] {"email", "foo"};
      var redirectUri = "http://redirect";
      var state = UUID.randomUUID().toString();
      var oauthcode = UUID.randomUUID().toString();

      mockSamUser(inputLinkedAccount.getUserId(), accessToken);

      when(providerServiceMock.createLink(
              inputLinkedAccount.getProviderName(),
              inputLinkedAccount.getUserId(),
              oauthcode,
              redirectUri,
              Set.of(scopes),
              state))
          .thenReturn(
              Optional.of(
                  new LinkedAccountWithPassportAndVisas.Builder()
                      .linkedAccount(inputLinkedAccount)
                      .build()));

      mvc.perform(
              post("/api/oidc/v1/{provider}/oauthcode", inputLinkedAccount.getProviderName())
                  .header("authorization", "Bearer " + accessToken)
                  .param("scopes", scopes)
                  .param("redirectUri", redirectUri)
                  .param("state", state)
                  .param("oauthcode", oauthcode))
          .andExpect(status().isOk())
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          oidcApiController.getLinkInfoFromLinkedAccount(inputLinkedAccount))));
    }

    @Test
    void testExceptionIsLogged() throws Exception {
      var accessToken = "testToken";

      mockSamUser("userId", accessToken);

      when(providerServiceMock.createLink(any(), any(), any(), any(), any(), any()))
          .thenThrow(new ExternalCredsException("This is a drill!"));

      // check that an internal server error code is returned
      mvc.perform(
              post("/api/oidc/v1/{provider}/oauthcode", "testProviderName")
                  .header("authorization", "Bearer " + accessToken)
                  .param("scopes", new String[] {"foo"})
                  .param("redirectUri", "redirectUri")
                  .param("state", "state")
                  .param("oauthcode", "oauthcode"))
          .andExpect(status().isInternalServerError());

      // check that a log was recorded
      verify(auditLoggerMock).logEvent(any());
    }
  }

  @Nested
  class DeleteLink {

    @Test
    void testDeleteLink() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var providerName = UUID.randomUUID().toString();
      mockSamUser(userId, accessToken);

      doNothing().when(providerServiceMock).deleteLink(userId, providerName);

      mvc.perform(
              delete("/api/oidc/v1/{provider}", providerName)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk());

      verify(providerServiceMock).deleteLink(userId, providerName);
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
      var passport = TestUtils.createRandomPassport();

      mockSamUser(userId, accessToken);

      when(passportServiceMock.getPassport(userId, providerName)).thenReturn(Optional.of(passport));

      mvc.perform(
              get("/api/oidc/v1/{provider}/passport", providerName)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk())
          .andExpect(content().json("\"" + passport.getJwt() + "\""));
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

  private void mockSamUser(String userId, String accessToken) throws ApiException {
    var usersApiMock = mock(UsersApi.class);
    var userStatusInfo = new UserStatusInfo().userSubjectId(userId);
    when(samServiceMock.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo()).thenReturn(userStatusInfo);
  }

  private void mockSamUserError(String accessToken, HttpStatus notFound) throws ApiException {
    var usersApiMock = mock(UsersApi.class);
    when(samServiceMock.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo())
        .thenThrow(new ApiException(notFound.value(), "Not Found"));
  }
}
