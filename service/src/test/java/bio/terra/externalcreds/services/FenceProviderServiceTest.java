package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.OAuth2State;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class FenceProviderServiceTest extends BaseTest {

  @Autowired private FenceProviderService fenceProviderService;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private BondService bondService;
  @MockBean private FenceAccountKeyService fenceAccountKeyService;
  @MockBean private ProviderOAuthClientCache providerOAuthClientCache;
  @MockBean private OAuth2Service oAuth2Service;
  @MockBean private ExternalCredsConfig externalCredsConfig;

  private static final Random random = new Random();

  @Test
  void testGetLinkedFenceAccountKey() {
    var linkedAccountId = random.nextInt();
    var fenceKeyId = random.nextInt();
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
            .id(linkedAccountId)
            .externalUserId(userName)
            .refreshToken(token)
            .isAuthenticated(true)
            .build();
    when(linkedAccountService.getLinkedAccount(userId, provider))
        .thenReturn(Optional.of(linkedAccount));

    var fenceAccountKey =
        new FenceAccountKey.Builder()
            .id(fenceKeyId)
            .linkedAccountId(linkedAccountId)
            .keyJson(keyJson)
            .expiresAt(issuedAt.plus(30, ChronoUnit.DAYS))
            .build();
    when(fenceAccountKeyService.getFenceAccountKey(linkedAccount))
        .thenReturn(Optional.of(fenceAccountKey));

    var actualFenceAccountKey = fenceProviderService.getFenceAccountKey(linkedAccount);

    assertPresent(actualFenceAccountKey);
  }

  @Test
  void testCreateLink() {
    var linkedAccountId = random.nextInt();
    var linkedAccount = TestUtils.createRandomLinkedAccount(Provider.FENCE);
    when(linkedAccountService.upsertLinkedAccount(any()))
        .thenReturn(linkedAccount.withId(linkedAccountId));
    var auditLogEventBuilder = new AuditLogEvent.Builder().userId(linkedAccount.getUserId());
    var oauth2State = setupMocksforCreateLink(linkedAccount);

    var actualLinkedAccount =
        fenceProviderService.createLink(
            linkedAccount.getProvider(),
            linkedAccount.getUserId(),
            "code",
            oauth2State.encode(objectMapper),
            auditLogEventBuilder);
    assertEquals(linkedAccount.withId(linkedAccountId), actualLinkedAccount);
  }

  @Test
  void testCreateLinkFails() {
    var linkedAccount = TestUtils.createRandomLinkedAccount(Provider.FENCE);
    var auditLogEventBuilder = new AuditLogEvent.Builder().userId(linkedAccount.getUserId());

    var oAuth2State = setupMocksforCreateLink(linkedAccount);
    when(oAuth2Service.authorizationCodeExchange(any(), any(), any(), any(), any(), any()))
        .thenThrow(new OAuth2AuthorizationException(new OAuth2Error("error")));

    assertThrows(
        BadRequestException.class,
        () ->
            fenceProviderService.createLink(
                linkedAccount.getProvider(),
                linkedAccount.getUserId(),
                "code",
                oAuth2State.encode(objectMapper),
                auditLogEventBuilder));
  }

  private OAuth2State setupMocksforCreateLink(LinkedAccount linkedAccount) {
    var oauth2State =
        new OAuth2State.Builder()
            .provider(Provider.FENCE)
            .random("abcde")
            .redirectUri("http://localhost:8080/oauth2/callback")
            .build();
    var encodedState = oauth2State.encode(objectMapper);
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProvider().toString())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();
    when(providerOAuthClientCache.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(providerClient);
    doNothing()
        .when(linkedAccountService)
        .validateAndDeleteOAuth2State(linkedAccount.getUserId(), oauth2State);

    var provider = TestUtils.createRandomProvider().setScopes(Set.of("scope"));
    when(externalCredsConfig.getProviderProperties(linkedAccount.getProvider()))
        .thenReturn(provider);
    var accessTokenResponse =
        OAuth2AccessTokenResponse.withToken("token")
            .refreshToken("refresh")
            .tokenType(OAuth2AccessToken.TokenType.BEARER)
            .build();
    when(oAuth2Service.authorizationCodeExchange(
            providerClient,
            "code",
            "http://localhost:8080/oauth2/callback",
            Set.copyOf(provider.getScopes()),
            oauth2State.encode(objectMapper),
            Map.of()))
        .thenReturn(accessTokenResponse);

    when(oAuth2Service.getUserInfo(providerClient, accessTokenResponse.getAccessToken()))
        .thenReturn(
            new DefaultOAuth2User(
                null,
                Map.of("foo", "bar", provider.getExternalIdClaim(), "barName"),
                provider.getExternalIdClaim()));
    return oauth2State;
  }

  @Nested
  class BondFenceAccounts {

    @Test
    void testGetLinkedFenceAccountExistsInBond() {
      var linkedAccountId = random.nextInt();
      var issuedAt = Instant.now();
      var token = "TestToken";
      var userId = UUID.randomUUID().toString();
      var userName = userId + "-name";
      var keyJson = "{ \"name\": \"testKeyJson\"}";
      var provider = Provider.FENCE;

      var bondLinkedAccount =
          new LinkedAccount.Builder()
              .externalUserId(userName)
              .refreshToken(token)
              .userId(userId)
              .expires(new Timestamp(issuedAt.plus(Duration.ofDays(30)).toEpochMilli()))
              .provider(provider)
              .isAuthenticated(true)
              .build();

      var ecmLinkedAccount = bondLinkedAccount.withId(linkedAccountId);
      when(bondService.getLinkedAccount(userId, provider))
          .thenReturn(Optional.of(bondLinkedAccount));
      when(linkedAccountService.getLinkedAccount(userId, provider)).thenReturn(Optional.empty());

      when(linkedAccountService.upsertLinkedAccount(bondLinkedAccount))
          .thenReturn(ecmLinkedAccount);

      var key =
          new FenceAccountKey.Builder()
              .keyJson(keyJson)
              .expiresAt(issuedAt.plus(Duration.ofDays(30)))
              .linkedAccountId(linkedAccountId)
              .build();

      when(bondService.getFenceServiceAccountKey(userId, provider, linkedAccountId))
          .thenReturn(Optional.of(key));

      var linkedAccount = fenceProviderService.getLinkedFenceAccount(userId, provider);

      assertPresent(linkedAccount);

      when(linkedAccountService.getLinkedAccount(userId, provider))
          .thenReturn(Optional.of(ecmLinkedAccount));
      when(fenceAccountKeyService.getFenceAccountKey(ecmLinkedAccount))
          .thenReturn(Optional.of(key));

      var fenceKey = fenceProviderService.getFenceAccountKey(linkedAccount.get());

      assertPresent(fenceKey);
    }

    @Test
    void testGetLinkedFenceAccountDoesNotExistsInBond() {
      var userId = UUID.randomUUID().toString();
      var provider = Provider.FENCE;

      when(bondService.getLinkedAccount(anyString(), any(Provider.class)))
          .thenReturn(Optional.empty());

      var linkedAccount = fenceProviderService.getLinkedFenceAccount(userId, provider);

      assertEmpty(linkedAccount);
    }

    @Test
    void testGetLinkedFenceAccountExistsInBondAndEcmUpToDate() {
      var linkedAccountId = random.nextInt();
      var issuedAt = Instant.now().minus(Duration.ofDays(90));
      var token = "TestToken";
      var provider = Provider.FENCE;

      var expiration = new Timestamp(Instant.now().plus(Duration.ofDays(30)).toEpochMilli());

      var ecmLinkedAccount =
          TestUtils.createRandomLinkedAccount(Provider.FENCE)
              .withExpires(expiration)
              .withId(linkedAccountId);

      var bondLinkedAccount =
          new LinkedAccount.Builder()
              .provider(provider)
              .expires(new Timestamp(issuedAt.toEpochMilli()))
              .userId(ecmLinkedAccount.getUserId())
              .externalUserId(ecmLinkedAccount.getExternalUserId())
              .refreshToken(token)
              .isAuthenticated(true)
              .build();

      when(linkedAccountService.upsertLinkedAccount(bondLinkedAccount))
          .thenReturn(ecmLinkedAccount);
      when(bondService.getLinkedAccount(anyString(), any(Provider.class)))
          .thenReturn(Optional.of(bondLinkedAccount));

      var linkedAccountFromEcm =
          fenceProviderService.getLinkedFenceAccount(ecmLinkedAccount.getUserId(), provider);

      assertPresent(linkedAccountFromEcm);
      assertEquals(expiration, linkedAccountFromEcm.get().getExpires());
    }

    @Test
    void testGetLinkedFenceAccountExistsInBondAndEcmOutOfDate() {
      var linkedAccountId = random.nextInt();
      var issuedAt = Instant.now();
      var token = "TestToken";
      var provider = Provider.FENCE;

      var expiration = new Timestamp(Instant.now().minus(Duration.ofDays(90)).toEpochMilli());
      var expectedExpiresAt = new Timestamp(issuedAt.plus(30, ChronoUnit.DAYS).toEpochMilli());
      var ecmLinkedAccount =
          TestUtils.createRandomLinkedAccount(Provider.FENCE)
              .withExpires(expiration)
              .withId(linkedAccountId);

      var bondLinkedAccount =
          new LinkedAccount.Builder()
              .provider(provider)
              .expires(expectedExpiresAt)
              .userId(ecmLinkedAccount.getUserId())
              .externalUserId(ecmLinkedAccount.getExternalUserId())
              .refreshToken(token)
              .isAuthenticated(true)
              .build();

      when(linkedAccountService.getLinkedAccount(ecmLinkedAccount.getUserId(), provider))
          .thenReturn(Optional.of(ecmLinkedAccount));
      when(linkedAccountService.upsertLinkedAccount(bondLinkedAccount))
          .thenReturn(bondLinkedAccount.withId(linkedAccountId));
      when(bondService.getLinkedAccount(ecmLinkedAccount.getUserId(), provider))
          .thenReturn(Optional.of(bondLinkedAccount));
      when(bondService.getFenceServiceAccountKey(
              ecmLinkedAccount.getUserId(), provider, linkedAccountId))
          .thenReturn(Optional.empty());

      var linkedAccount =
          fenceProviderService.getLinkedFenceAccount(ecmLinkedAccount.getUserId(), provider);

      assertPresent(linkedAccount);
      assertEquals(expectedExpiresAt, linkedAccount.get().getExpires());
    }

    @Test
    void testDeleteBondFenceLink() {
      var userId = UUID.randomUUID().toString();
      var provider = Provider.FENCE;
      doNothing().when(bondService).deleteBondLinkedAccount(userId, provider);
      fenceProviderService.deleteBondFenceLink(userId, provider);
    }

    @Test
    void testDeleteBondFenceLinkFailsGracefully() {
      var userId = UUID.randomUUID().toString();
      var provider = Provider.FENCE;
      doThrow(new RuntimeException()).when(bondService).deleteBondLinkedAccount(userId, provider);
      fenceProviderService.deleteBondFenceLink(userId, provider);
    }
  }
}
