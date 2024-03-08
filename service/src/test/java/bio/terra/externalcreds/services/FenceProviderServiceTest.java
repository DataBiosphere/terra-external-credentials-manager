package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.dataAccess.DistributedLockDAO;
import bio.terra.externalcreds.dataAccess.FenceAccountKeyDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.DistributedLock;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

class FenceProviderServiceTest extends BaseTest {

  @Autowired private FenceProviderService fenceProviderService;

  @MockBean private LinkedAccountDAO linkedAccountDAO;
  @MockBean private FenceAccountKeyDAO fenceAccountKeyDAO;
  @MockBean private ProviderOAuthClientCache providerOAuthClientCache;
  @MockBean private ExternalCredsConfig externalCredsConfig;
  @MockBean private OAuth2Service oAuth2Service;

  @SpyBean private DistributedLockDAO distributedLockDAO;

  @Nested
  class FenceAccountKeyLocking {

    @Test
    void testCreateKeyNoExistingLock() {
      var userId = UUID.randomUUID().toString();
      var provider = Provider.FENCE;
      var issuedAt = Instant.now();
      var token = "TestToken";
      var userName = userId + "-name";

      var linkedAccount =
          new LinkedAccount.Builder()
              .provider(provider)
              .expires(new Timestamp(issuedAt.plus(30, ChronoUnit.DAYS).toEpochMilli()))
              .userId(userId)
              .id(1)
              .externalUserId(userName)
              .refreshToken(token)
              .isAuthenticated(true)
              .build();
      when(linkedAccountDAO.getLinkedAccount(userId, provider))
          .thenReturn(Optional.of(linkedAccount));

      when(externalCredsConfig.getProviderProperties(provider))
          .thenReturn(mock(ProviderProperties.class));
      var clientRegistration = mock(ClientRegistration.class);
      when(providerOAuthClientCache.getProviderClient(any())).thenReturn(clientRegistration);
      var oauth2AccessTokenResponse = mock(OAuth2AccessTokenResponse.class);
      when(oAuth2Service.authorizeWithRefreshToken(
              clientRegistration, new OAuth2RefreshToken(token, null)))
          .thenReturn(oauth2AccessTokenResponse);
      when(oauth2AccessTokenResponse.getAccessToken())
          .thenReturn(
              new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "accessToken", null, null));

      try (var mockServer = ClientAndServer.startClientAndServer()) {
        var credentialsPath = "/test/credentials/";
        var providerInfo =
            TestUtils.createRandomProvider()
                .setKeyEndpoint("http://localhost:" + mockServer.getPort() + credentialsPath);

        when(externalCredsConfig.getProviderProperties(linkedAccount.getProvider()))
            .thenReturn(providerInfo);

        //  Mock the server response
        mockServer
            .when(
                HttpRequest.request(credentialsPath)
                    .withMethod("POST")
                    .withHeader("Authorization", "Bearer accessToken"))
            .respond(
                HttpResponse.response()
                    .withStatusCode(200)
                    .withBody("{ \"name\": \"testKeyJson\"}"));

        fenceProviderService.getLinkedFenceAccountKey(userId, provider);
      }
    }

    @Test
    void testFailsIfEncountersLock() {
      var userId = UUID.randomUUID().toString();
      var provider = Provider.FENCE;
      var issuedAt = Instant.now();
      var token = "TestToken";
      var userName = userId + "-name";

      var linkedAccount =
          new LinkedAccount.Builder()
              .provider(provider)
              .expires(new Timestamp(issuedAt.plus(30, ChronoUnit.DAYS).toEpochMilli()))
              .userId(userId)
              .id(1)
              .externalUserId(userName)
              .refreshToken(token)
              .isAuthenticated(true)
              .build();
      when(linkedAccountDAO.getLinkedAccount(userId, provider))
          .thenReturn(Optional.of(linkedAccount));
      var lockName =
          String.format("%s-retrieveFenceAccountKey", linkedAccount.getProvider().toString());
      distributedLockDAO.insertDistributedLock(
          new DistributedLock.Builder()
              .lockName(lockName)
              .userId(userId)
              .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
              .build());

      assertThrows(
          ExternalCredsException.class,
          () -> fenceProviderService.getLinkedFenceAccountKey(userId, provider));
    }
  }

  @Test
  void testGetLinkedFenceAccountKey() {
    var issuedAt = Instant.now();
    var token = "TestToken";
    var userId = UUID.randomUUID().toString();
    var userName = userId + "-name";
    var keyJson = "{ \"name\": \"testKeyJson\"}";
    var provider = Provider.FENCE;

    var linkedAccount =
        new LinkedAccount.Builder()
            .provider(provider)
            .expires(new Timestamp(issuedAt.plus(30, ChronoUnit.DAYS).toEpochMilli()))
            .userId(userId)
            .id(1)
            .externalUserId(userName)
            .refreshToken(token)
            .isAuthenticated(true)
            .build();
    when(linkedAccountDAO.getLinkedAccount(userId, provider))
        .thenReturn(Optional.of(linkedAccount));

    var fenceAccountKey =
        new FenceAccountKey.Builder()
            .id(1)
            .linkedAccountId(1)
            .keyJson(keyJson)
            .expiresAt(issuedAt.plus(30, ChronoUnit.DAYS))
            .build();
    when(fenceAccountKeyDAO.getFenceAccountKey(linkedAccount))
        .thenReturn(Optional.of(fenceAccountKey));

    var actualFenceAccountKey = fenceProviderService.getLinkedFenceAccountKey(userId, provider);

    assertPresent(actualFenceAccountKey);
  }

  @Test
  void createLink() {}
}
