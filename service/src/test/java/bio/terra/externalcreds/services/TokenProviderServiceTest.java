package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEvent.Builder;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.AccessTokenCacheEntry;
import bio.terra.externalcreds.models.LinkedAccount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

public class TokenProviderServiceTest extends BaseTest {

  @Autowired private TokenProviderService tokenProviderService;
  @MockBean private AuditLogger auditLoggerMock;
  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private FenceProviderService fenceProviderService;
  @MockBean private ProviderTokenClientCache providerTokenClientCacheMock;
  @MockBean private OAuth2Service oAuth2ServiceMock;
  @MockBean private AccessTokenCacheService accessTokenCacheService;

  private final Provider provider = Provider.GITHUB;
  private final String userId = UUID.randomUUID().toString();
  private final String clientIP = "127.0.0.1";
  private final AuditLogEvent.Builder auditLogEventBuilder =
      new Builder().provider(provider).userId(userId).clientIP(clientIP);

  private Random random = new Random();

  @Test
  void testLogLinkCreateSuccess() {
    Optional<LinkedAccount> linkedAccount =
        Optional.ofNullable(TestUtils.createRandomLinkedAccount(provider));
    tokenProviderService.logLinkCreation(linkedAccount, auditLogEventBuilder);
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.LinkCreated)
                .provider(provider)
                .userId(userId)
                .externalUserId(linkedAccount.map(LinkedAccount::getExternalUserId))
                .clientIP(clientIP)
                .build());
  }

  @Test
  void testLogLinkCreateFailure() {
    tokenProviderService.logLinkCreation(Optional.empty(), auditLogEventBuilder);
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.LinkCreationFailed)
                .provider(provider)
                .userId(userId)
                .externalUserId(Optional.empty())
                .clientIP(clientIP)
                .build());
  }

  @Test
  void testGetProviderAccessTokenSuccess() {
    var linkedAccount = TestUtils.createRandomLinkedAccount(provider).withId(random.nextInt());
    var clientRegistration = createClientRegistration(linkedAccount.getProvider());

    when(linkedAccountService.getLinkedAccount(linkedAccount.getUserId(), provider))
        .thenReturn(Optional.of(linkedAccount));
    when(accessTokenCacheService.getAccessTokenCacheEntry(
            linkedAccount.getUserId(), linkedAccount.getProvider()))
        .thenReturn(Optional.empty());
    when(accessTokenCacheService.upsertAccessTokenCacheEntry(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, AccessTokenCacheEntry.class));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);

    var accessToken = "tokenValue";
    var updatedRefreshToken = "newRefreshToken";
    var oAuth2TokenResponse =
        OAuth2AccessTokenResponse.withToken(accessToken)
            .refreshToken(updatedRefreshToken)
            .tokenType(OAuth2AccessToken.TokenType.BEARER)
            .build();
    when(oAuth2ServiceMock.authorizeWithRefreshToken(
            clientRegistration, new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null)))
        .thenReturn(oAuth2TokenResponse);
    var updatedLinkedAccount =
        linkedAccount.withRefreshToken(oAuth2TokenResponse.getRefreshToken().getTokenValue());
    when(linkedAccountService.upsertLinkedAccount(updatedLinkedAccount))
        .thenReturn(updatedLinkedAccount);

    var auditLogEventBuilder =
        new Builder().provider(provider).userId(linkedAccount.getUserId()).clientIP(clientIP);
    Optional<String> response =
        tokenProviderService.getProviderAccessToken(
            linkedAccount.getUserId(), provider, auditLogEventBuilder);
    assertEquals(response.get(), accessToken);
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.GetProviderAccessToken)
                .provider(provider)
                .userId(linkedAccount.getUserId())
                .clientIP(clientIP)
                .externalUserId(linkedAccount.getExternalUserId())
                .build());
  }

  @Test
  void testGetFenceProviderAccessTokenSuccess() {
    var provider = Provider.FENCE;
    var linkedAccount = TestUtils.createRandomLinkedAccount(provider).withId(random.nextInt());
    var clientRegistration = createClientRegistration(linkedAccount.getProvider());

    when(fenceProviderService.getLinkedFenceAccount(linkedAccount.getUserId(), provider))
        .thenReturn(Optional.of(linkedAccount));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);
    when(accessTokenCacheService.getAccessTokenCacheEntry(
            linkedAccount.getUserId(), linkedAccount.getProvider()))
        .thenReturn(Optional.empty());
    when(accessTokenCacheService.upsertAccessTokenCacheEntry(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, AccessTokenCacheEntry.class));

    var accessToken = "tokenValue";
    var updatedRefreshToken = "newRefreshToken";
    var oAuth2TokenResponse =
        OAuth2AccessTokenResponse.withToken(accessToken)
            .refreshToken(updatedRefreshToken)
            .tokenType(OAuth2AccessToken.TokenType.BEARER)
            .build();
    when(oAuth2ServiceMock.authorizeWithRefreshToken(
            clientRegistration, new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null)))
        .thenReturn(oAuth2TokenResponse);
    var updatedLinkedAccount =
        linkedAccount.withRefreshToken(oAuth2TokenResponse.getRefreshToken().getTokenValue());
    when(linkedAccountService.upsertLinkedAccount(updatedLinkedAccount))
        .thenReturn(updatedLinkedAccount);

    var auditLogEventBuilder =
        new Builder().provider(provider).userId(linkedAccount.getUserId()).clientIP(clientIP);
    Optional<String> response =
        tokenProviderService.getProviderAccessToken(
            linkedAccount.getUserId(), provider, auditLogEventBuilder);
    assertEquals(response.get(), accessToken);
    verify(oAuth2ServiceMock, never())
        .authorizationCodeExchange(any(), any(), any(), any(), any(), any());
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.GetProviderAccessToken)
                .provider(provider)
                .userId(linkedAccount.getUserId())
                .clientIP(clientIP)
                .externalUserId(linkedAccount.getExternalUserId())
                .build());
  }

  @Test
  void testGetFenceProviderAccessTokenCacheSuccess() {
    var provider = Provider.FENCE;
    var linkedAccount = TestUtils.createRandomLinkedAccount(provider).withId(random.nextInt());
    var clientRegistration = createClientRegistration(linkedAccount.getProvider());
    var accessToken = UUID.randomUUID().toString();
    var tokenExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

    when(fenceProviderService.getLinkedFenceAccount(linkedAccount.getUserId(), provider))
        .thenReturn(Optional.of(linkedAccount));
    when(accessTokenCacheService.getAccessTokenCacheEntry(
            linkedAccount.getUserId(), linkedAccount.getProvider()))
        .thenReturn(
            Optional.of(
                new AccessTokenCacheEntry.Builder()
                    .linkedAccountId(linkedAccount.getId().get())
                    .accessToken(accessToken)
                    .expiresAt(tokenExpiresAt)
                    .build()));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);
    when(accessTokenCacheService.upsertAccessTokenCacheEntry(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, AccessTokenCacheEntry.class));

    var auditLogEventBuilder =
        new Builder().provider(provider).userId(linkedAccount.getUserId()).clientIP(clientIP);
    Optional<String> response =
        tokenProviderService.getProviderAccessToken(
            linkedAccount.getUserId(), provider, auditLogEventBuilder);
    assertEquals(response.get(), accessToken);
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.GetProviderAccessToken)
                .provider(provider)
                .userId(linkedAccount.getUserId())
                .clientIP(clientIP)
                .build());
  }

  @Test
  void testGetFenceProviderAccessTokenCacheExpired() {
    var provider = Provider.FENCE;
    var linkedAccount = TestUtils.createRandomLinkedAccount(provider).withId(random.nextInt());
    var clientRegistration = createClientRegistration(linkedAccount.getProvider());
    var accessToken = UUID.randomUUID().toString();
    var tokenExpiresAt = Instant.now().minus(1, ChronoUnit.HOURS);

    when(fenceProviderService.getLinkedFenceAccount(linkedAccount.getUserId(), provider))
        .thenReturn(Optional.of(linkedAccount));
    when(accessTokenCacheService.getAccessTokenCacheEntry(
            linkedAccount.getUserId(), linkedAccount.getProvider()))
        .thenReturn(
            Optional.of(
                new AccessTokenCacheEntry.Builder()
                    .linkedAccountId(linkedAccount.getId().get())
                    .accessToken(accessToken)
                    .expiresAt(tokenExpiresAt)
                    .build()));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);
    when(accessTokenCacheService.upsertAccessTokenCacheEntry(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, AccessTokenCacheEntry.class));

    var updatedRefreshToken = "newRefreshToken";
    var oAuth2TokenResponse =
        OAuth2AccessTokenResponse.withToken(accessToken)
            .refreshToken(updatedRefreshToken)
            .tokenType(OAuth2AccessToken.TokenType.BEARER)
            .build();
    when(oAuth2ServiceMock.authorizeWithRefreshToken(
            clientRegistration, new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null)))
        .thenReturn(oAuth2TokenResponse);
    var updatedLinkedAccount =
        linkedAccount.withRefreshToken(oAuth2TokenResponse.getRefreshToken().getTokenValue());
    when(linkedAccountService.upsertLinkedAccount(updatedLinkedAccount))
        .thenReturn(updatedLinkedAccount);

    var auditLogEventBuilder =
        new Builder().provider(provider).userId(linkedAccount.getUserId()).clientIP(clientIP);
    Optional<String> response =
        tokenProviderService.getProviderAccessToken(
            linkedAccount.getUserId(), provider, auditLogEventBuilder);
    assertEquals(response.get(), accessToken);
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.GetProviderAccessToken)
                .provider(provider)
                .userId(linkedAccount.getUserId())
                .clientIP(clientIP)
                .externalUserId(linkedAccount.getExternalUserId())
                .build());
  }

  @Test
  void testGetProviderAccessTokenNoLinkedAccount() {
    var userId = "fakeUserId";
    var provider = Provider.GITHUB;

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.GetProviderAccessToken)
            .provider(provider)
            .userId(userId)
            .externalUserId(Optional.empty())
            .clientIP(clientIP);

    when(linkedAccountService.getLinkedAccount(userId, provider)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> tokenProviderService.getProviderAccessToken(userId, provider, auditLogEventBuilder));
  }

  @Test
  void testGetProviderAccessTokenUnauthorized() {
    var linkedAccount = TestUtils.createRandomLinkedAccount(provider);
    var clientRegistration = createClientRegistration(linkedAccount.getProvider());

    when(linkedAccountService.getLinkedAccount(linkedAccount.getUserId(), provider))
        .thenReturn(Optional.of(linkedAccount));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);
    when(oAuth2ServiceMock.authorizeWithRefreshToken(
            clientRegistration, new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null)))
        .thenThrow(
            new OAuth2AuthorizationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN)));

    assertThrows(
        OAuth2AuthorizationException.class,
        () ->
            tokenProviderService.getProviderAccessToken(
                linkedAccount.getUserId(), provider, auditLogEventBuilder));
  }

  private ClientRegistration createClientRegistration(Provider provider) {
    return ClientRegistration.withRegistrationId(provider.toString())
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .build();
  }
}
