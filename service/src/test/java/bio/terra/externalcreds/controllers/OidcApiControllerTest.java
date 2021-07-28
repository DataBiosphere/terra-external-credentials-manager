package bio.terra.externalcreds.controllers;

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
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.SamService;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

@AutoConfigureMockMvc
public class OidcApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private ProviderService providerService;
  @MockBean private SamService samService;

  @Test
  void testListProviders() throws Exception {
    when(providerService.getProviderList()).thenReturn(Set.of("fake-provider2", "fake-provider1"));

    mvc.perform(get("/api/oidc/v1/providers"))
        .andExpect(content().json("[\"fake-provider1\",\"fake-provider2\"]"));
  }

  @Test
  void testGetAuthUrl() throws Exception {
    var result = "https://test/authorization/uri";
    var provider = "fake";
    var redirectUri = "fakeuri";
    var scopes = Set.of("openid", "email");
    String state = null;

    when(providerService.getProviderAuthorizationUrl(provider, redirectUri, scopes, state))
        .thenReturn(result);

    var queryParams = new LinkedMultiValueMap<String, String>();
    queryParams.add("redirectUri", redirectUri);
    queryParams.addAll("scopes", List.copyOf(scopes));
    mvc.perform(get("/api/oidc/v1/{provider}/authorization-url", provider).queryParams(queryParams))
        .andExpect(content().json("\"" + result + "\""));
  }

  @Test
  void testGetAuthUrl404() throws Exception {
    var provider = "fake";
    var redirectUri = "fakeuri";
    var scopes = Set.of("openid", "email");
    String state = null;

    when(providerService.getProviderAuthorizationUrl(provider, redirectUri, scopes, state))
        .thenReturn(null);

    var queryParams = new LinkedMultiValueMap<String, String>();
    queryParams.add("redirectUri", redirectUri);
    queryParams.addAll("scopes", List.copyOf(scopes));
    mvc.perform(get("/api/oidc/v1/{provider}/authorization-url", provider).queryParams(queryParams))
        .andExpect(status().isNotFound());
  }

  @Test
  void testGetLink() throws Exception {
    var accessToken = "testToken";
    var userId = UUID.randomUUID().toString();
    var inputLinkedAccount =
        LinkedAccount.builder()
            .userId(userId)
            .providerId("testProvider")
            .externalUserId("externalUser")
            .expires(Timestamp.valueOf("2007-09-23 10:10:10.0"))
            .build();

    mockSamUser(userId, accessToken);

    when(linkedAccountService.getLinkedAccount(
            eq(inputLinkedAccount.getUserId()), eq(inputLinkedAccount.getProviderId())))
        .thenReturn(Optional.of(inputLinkedAccount));

    mvc.perform(
            get("/api/oidc/v1/" + inputLinkedAccount.getProviderId())
                .header("authorization", "Bearer " + accessToken))
        .andExpect(
            content()
                .json(
                    "{\"externalUserId\":\""
                        + inputLinkedAccount.getExternalUserId()
                        + "\", \"expirationTimestamp\":\""
                        + OffsetDateTime.ofInstant(
                            inputLinkedAccount.getExpires().toInstant(), ZoneId.of("UTC"))
                        + "\"}"));
  }

  @Test
  void testGetLinkedAccount404() throws Exception {
    var userId = "non-existent-user";
    var providerId = "non-existent-provider";
    var accessToken = "testToken";

    mockSamUser(userId, accessToken);

    mvc.perform(get("/api/oidc/v1/" + providerId).header("authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void testGetLinkedAccount403() throws Exception {
    var providerId = "provider";
    var accessToken = "testToken";

    mockSamUserError(accessToken, HttpStatus.NOT_FOUND);

    mvc.perform(get("/api/oidc/v1/" + providerId).header("authorization", "Bearer " + accessToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void testGetLinkedAccount500() throws Exception {
    String providerId = "provider";
    String accessToken = "testToken";

    mockSamUserError(accessToken, HttpStatus.NO_CONTENT);

    mvc.perform(get("/api/oidc/v1/" + providerId).header("authorization", "Bearer " + accessToken))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void testCreateLink() throws Exception {
    var accessToken = "testToken";
    var userId = UUID.randomUUID().toString();
    var inputLinkedAccount =
        LinkedAccount.builder()
            .userId(userId)
            .providerId("testProvider")
            .externalUserId("externalUser")
            .expires(Timestamp.valueOf("2007-09-23 10:10:10.0"))
            .build();

    var scopes = new String[] {"email", "foo"};
    var redirectUri = "http://redirect";
    var state = UUID.randomUUID().toString();
    var oauthcode = UUID.randomUUID().toString();

    mockSamUser(userId, accessToken);

    when(providerService.createLink(
            inputLinkedAccount.getProviderId(),
            inputLinkedAccount.getUserId(),
            oauthcode,
            redirectUri,
            Set.of(scopes),
            state))
        .thenReturn(
            LinkedAccountWithPassportAndVisas.builder().linkedAccount(inputLinkedAccount).build());

    mvc.perform(
            post("/api/oidc/v1/{provider}/oauthcode", inputLinkedAccount.getProviderId())
                .header("authorization", "Bearer " + accessToken)
                .param("scopes", scopes)
                .param("redirectUri", redirectUri)
                .param("state", state)
                .param("oauthcode", oauthcode))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"externalUserId\":\""
                        + inputLinkedAccount.getExternalUserId()
                        + "\", \"expirationTimestamp\":\""
                        + OffsetDateTime.ofInstant(
                            inputLinkedAccount.getExpires().toInstant(), ZoneId.of("UTC"))
                        + "\"}"));
  }

  @Test
  void testDeleteLink() throws Exception {
    var accessToken = "testToken";
    var userId = UUID.randomUUID().toString();
    var providerId = UUID.randomUUID().toString();
    mockSamUser(userId, accessToken);

    doNothing().when(providerService).deleteLink(userId, providerId);

    mvc.perform(
            delete("/api/oidc/v1/{provider}", providerId)
                .header("authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    verify(providerService).deleteLink(userId, providerId);
  }

  @Test
  void testDeleteLinkNotFound() throws Exception {
    var accessToken = "testToken";
    var userId = UUID.randomUUID().toString();
    var providerId = UUID.randomUUID().toString();
    mockSamUser(userId, accessToken);

    doThrow(new NotFoundException("not found"))
        .when(providerService)
        .deleteLink(userId, providerId);

    mvc.perform(
            delete("/api/oidc/v1/{provider}", providerId)
                .header("authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  private void mockSamUser(String userId, String accessToken) throws ApiException {
    var usersApiMock = mock(UsersApi.class);
    var userStatusInfo = new UserStatusInfo().userSubjectId(userId);
    when(samService.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo()).thenReturn(userStatusInfo);
  }

  private void mockSamUserError(String accessToken, HttpStatus notFound) throws ApiException {
    var usersApiMock = mock(UsersApi.class);
    when(samService.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo())
        .thenThrow(new ApiException(notFound.value(), "Not Found"));
  }
}
