package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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

  @Test
  void testGetLinkedFenceAccountDoesNotExists() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;

    var linkedAccount = linkedAccountService.getLinkedAccount(userId, provider);

    assertEmpty(linkedAccount);
  }
}
