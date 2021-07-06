package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.SamService;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.util.MultiValueMap;

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
    String result = "https://test/authorization/uri";
    String provider = "fake";
    String redirectUri = "fakeuri";
    Set<String> scopes = Set.of("openid", "email");
    String state = null;

    when(providerService.getProviderAuthorizationUrl(provider, redirectUri, scopes, state))
        .thenReturn(result);

    MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
    queryParams.add("redirectUri", redirectUri);
    queryParams.addAll("scopes", List.copyOf(scopes));
    mvc.perform(get("/api/oidc/v1/{provider}/authorization-url", provider).queryParams(queryParams))
        .andExpect(content().json("\"" + result + "\""));
  }

  @Test
  void testGetAuthUrl404() throws Exception {
    String provider = "fake";
    String redirectUri = "fakeuri";
    Set<String> scopes = Set.of("openid", "email");
    String state = null;

    when(providerService.getProviderAuthorizationUrl(provider, redirectUri, scopes, state))
        .thenReturn(null);

    MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
    queryParams.add("redirectUri", redirectUri);
    queryParams.addAll("scopes", List.copyOf(scopes));
    mvc.perform(get("/api/oidc/v1/{provider}/authorization-url", provider).queryParams(queryParams))
        .andExpect(status().isNotFound());
  }

  @Test
  void testGetLink() throws Exception {
    String accessToken = "testToken";
    String userId = UUID.randomUUID().toString();
    LinkedAccount inputLinkedAccount =
        LinkedAccount.builder()
            .userId(userId)
            .providerId("testProvider")
            .externalUserId("externalUser")
            .expires(Timestamp.valueOf("2007-09-23 10:10:10.0"))
            .build();

    UsersApi usersApiMock = mock(UsersApi.class);
    UserStatusInfo userStatusInfo = new UserStatusInfo().userSubjectId(userId);
    when(samService.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo()).thenReturn(userStatusInfo);

    when(linkedAccountService.getLinkedAccount(
            eq(inputLinkedAccount.getUserId()), eq(inputLinkedAccount.getProviderId())))
        .thenReturn(inputLinkedAccount);

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
    String userId = "non-existent-user";
    String providerId = "non-existent-provider";
    String accessToken = "testToken";

    UsersApi usersApiMock = mock(UsersApi.class);
    UserStatusInfo userStatusInfo = new UserStatusInfo().userSubjectId(userId);
    when(samService.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo()).thenReturn(userStatusInfo);

    mvc.perform(get("/api/oidc/v1/" + providerId).header("authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void testGetLinkedAccount403() throws Exception {
    String providerId = "provider";
    String accessToken = "testToken";

    UsersApi usersApiMock = mock(UsersApi.class);
    when(samService.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo())
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), "Not Found"));

    mvc.perform(get("/api/oidc/v1/" + providerId).header("authorization", "Bearer " + accessToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void testGetLinkedAccount500() throws Exception {
    String providerId = "provider";
    String accessToken = "testToken";

    UsersApi usersApiMock = mock(UsersApi.class);
    when(samService.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo())
        .thenThrow(new ApiException(HttpStatus.NO_CONTENT.value(), "Not Found"));

    mvc.perform(get("/api/oidc/v1/" + providerId).header("authorization", "Bearer " + accessToken))
        .andExpect(status().isInternalServerError());
  }
}
