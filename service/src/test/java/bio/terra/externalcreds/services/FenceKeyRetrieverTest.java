package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.DistributedLockConfiguration;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.DistributedLockDAO;
import bio.terra.externalcreds.exception.DistributedLockException;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.DistributedLock;
import bio.terra.externalcreds.models.LinkedAccount;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

class FenceKeyRetrieverTest extends BaseTest {

  @Autowired private FenceKeyRetriever fenceKeyRetriever;
  @Autowired private LinkedAccountService linkedAccountService;

  @SpyBean private DistributedLockDAO distributedLockDAO;
  @SpyBean private FenceAccountKeyService fenceAccountKeyService;

  @MockBean private ExternalCredsConfig externalCredsConfig;
  @MockBean private ProviderOAuthClientCache providerOAuthClientCache;
  @MockBean private OAuth2Service oAuth2Service;
  @MockBean private AccessTokenCacheService accessTokenCacheService;

  @Nested
  class FenceAccountKeyLocking {

    @Test
    void testCreateKeyNoExistingLock() {
      var provider = Provider.FENCE;
      var keyJson = "{ \"name\": \"testKeyJson\", \"client_email\": \"foo@bar.com\"}";

      var linkedAccount =
          linkedAccountService.upsertLinkedAccount(TestUtils.createRandomLinkedAccount(provider));

      setupOAuthMocks(linkedAccount);

      try (var mockServer = ClientAndServer.startClientAndServer()) {
        var credentialsPath = "/test/credentials/";
        var providerInfo =
            TestUtils.createRandomProvider()
                .setKeyEndpoint("http://localhost:" + mockServer.getPort() + credentialsPath);

        when(externalCredsConfig.getProviderProperties(linkedAccount.getProvider()))
            .thenReturn(providerInfo);
        when(externalCredsConfig.getDistributedLockConfiguration())
            .thenReturn(
                DistributedLockConfiguration.create().setLockTimeout(Duration.ofSeconds(30)));

        //  Mock the server response
        mockServer
            .when(
                HttpRequest.request(credentialsPath)
                    .withMethod("POST")
                    .withHeader("Authorization", "Bearer accessToken"))
            .respond(HttpResponse.response().withStatusCode(200).withBody(keyJson));

        var key = fenceKeyRetriever.getOrCreateFenceAccountKey(linkedAccount);
        assertPresent(key);
        assertEquals(keyJson, key.get().getKeyJson());
      }
    }

    @Test
    void testCreateKeyFailsValidation() {
      var provider = Provider.FENCE;
      var keyJson = "{ \"name\": \"testKeyJson\"}";

      var linkedAccount =
          linkedAccountService.upsertLinkedAccount(TestUtils.createRandomLinkedAccount(provider));

      setupOAuthMocks(linkedAccount);

      try (var mockServer = ClientAndServer.startClientAndServer()) {
        var credentialsPath = "/test/credentials/";
        var providerInfo =
            TestUtils.createRandomProvider()
                .setKeyEndpoint("http://localhost:" + mockServer.getPort() + credentialsPath);

        when(externalCredsConfig.getProviderProperties(linkedAccount.getProvider()))
            .thenReturn(providerInfo);
        when(externalCredsConfig.getDistributedLockConfiguration())
            .thenReturn(
                DistributedLockConfiguration.create().setLockTimeout(Duration.ofSeconds(30)));

        //  Mock the server response
        mockServer
            .when(
                HttpRequest.request(credentialsPath)
                    .withMethod("POST")
                    .withHeader("Authorization", "Bearer accessToken"))
            .respond(HttpResponse.response().withStatusCode(200).withBody(keyJson));

        assertThrows(
            ExternalCredsException.class,
            () -> fenceKeyRetriever.getOrCreateFenceAccountKey(linkedAccount));
      }
    }

    @Test
    void testCreateKeyFailsIfNotJson() {
      var provider = Provider.FENCE;
      var keyJson = "notJson";

      var linkedAccount =
          linkedAccountService.upsertLinkedAccount(TestUtils.createRandomLinkedAccount(provider));

      setupOAuthMocks(linkedAccount);

      try (var mockServer = ClientAndServer.startClientAndServer()) {
        var credentialsPath = "/test/credentials/";
        var providerInfo =
            TestUtils.createRandomProvider()
                .setKeyEndpoint("http://localhost:" + mockServer.getPort() + credentialsPath);

        when(externalCredsConfig.getProviderProperties(linkedAccount.getProvider()))
            .thenReturn(providerInfo);
        when(externalCredsConfig.getDistributedLockConfiguration())
            .thenReturn(
                DistributedLockConfiguration.create().setLockTimeout(Duration.ofSeconds(30)));

        //  Mock the server response
        mockServer
            .when(
                HttpRequest.request(credentialsPath)
                    .withMethod("POST")
                    .withHeader("Authorization", "Bearer accessToken"))
            .respond(HttpResponse.response().withStatusCode(200).withBody(keyJson));

        assertThrows(
            ExternalCredsException.class,
            () -> fenceKeyRetriever.getOrCreateFenceAccountKey(linkedAccount));
      }
    }

    private void setupOAuthMocks(LinkedAccount linkedAccount) {
      var scopes = Set.of("scope1", "scope2");
      when(accessTokenCacheService.getLinkedAccountAccessToken(
              eq(linkedAccount), eq(scopes), any()))
          .thenReturn("accessToken");

      when(externalCredsConfig.getProviderProperties(linkedAccount.getProvider()))
          .thenReturn(TestUtils.createRandomProvider());

      var clientRegistration = mock(ClientRegistration.class);
      when(providerOAuthClientCache.getProviderClient(any())).thenReturn(clientRegistration);
      var oauth2AccessTokenResponse = mock(OAuth2AccessTokenResponse.class);
      when(oAuth2Service.authorizeWithRefreshToken(
              clientRegistration,
              new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null),
              scopes))
          .thenReturn(oauth2AccessTokenResponse);
      when(oauth2AccessTokenResponse.getAccessToken())
          .thenReturn(
              new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "accessToken", null, null));
    }

    @Test
    void testRetriesIfLockPresent() {
      var provider = Provider.FENCE;
      var linkedAccount =
          linkedAccountService.upsertLinkedAccount(TestUtils.createRandomLinkedAccount(provider));

      var newLock =
          new DistributedLock.Builder()
              .lockName("createFenceKey-" + linkedAccount.getProvider())
              .userId(linkedAccount.getUserId())
              .expiresAt(Instant.now().plus(2, ChronoUnit.MINUTES))
              .build();

      distributedLockDAO.insertDistributedLock(newLock);

      when(externalCredsConfig.getDistributedLockConfiguration())
          .thenReturn(DistributedLockConfiguration.create().setLockTimeout(Duration.ofSeconds(30)));

      assertThrows(
          DistributedLockException.class,
          () -> fenceKeyRetriever.getOrCreateFenceAccountKey(linkedAccount));

      // Test config is set to 3 retries
      verify(fenceAccountKeyService, times(3)).getFenceAccountKey(linkedAccount);
    }

    @Test
    void testRemovesLockIfKeyRetrievalFails() {
      var provider = Provider.FENCE;
      var linkedAccount =
          linkedAccountService.upsertLinkedAccount(TestUtils.createRandomLinkedAccount(provider));

      var scopes = Set.of("scope1", "scope2");
      var lockName = "createFenceKey-" + linkedAccount.getProvider();

      var clientRegistration = mock(ClientRegistration.class);
      when(providerOAuthClientCache.getProviderClient(any())).thenReturn(clientRegistration);
      when(oAuth2Service.authorizeWithRefreshToken(
              clientRegistration,
              new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null),
              scopes))
          .thenThrow(new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR));
      when(externalCredsConfig.getDistributedLockConfiguration())
          .thenReturn(DistributedLockConfiguration.create().setLockTimeout(Duration.ofSeconds(30)));

      assertThrows(
          ExternalCredsException.class,
          () -> fenceKeyRetriever.getOrCreateFenceAccountKey(linkedAccount));

      verify(distributedLockDAO)
          .insertDistributedLock(argThat(lock -> lock.getLockName().equals(lockName)));
      verify(distributedLockDAO).deleteDistributedLock(lockName, linkedAccount.getUserId());
    }
  }
}
