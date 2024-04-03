package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.dataAccess.AccessTokenCacheDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.AccessTokenCacheEntry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

class AccessTokenCacheServiceTest extends BaseTest {

  @Autowired private AccessTokenCacheService accessTokenCacheService;

  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private ProviderTokenClientCache providerTokenClientCacheMock;
  @MockBean private OAuth2Service oAuth2ServiceMock;
  @MockBean private AccessTokenCacheDAO accessTokenCacheDAO;
  @MockBean private AuditLogger auditLoggerMock;

  private final String clientIP = "127.0.0.1";
  private final Random random = new Random();

  @Test
  void testGetProviderAccessTokenSuccess() {
    var provider = Provider.GITHUB;
    var linkedAccount = TestUtils.createRandomLinkedAccount(provider).withId(random.nextInt());
    var clientRegistration = TestUtils.createClientRegistration(linkedAccount.getProvider());

    when(linkedAccountService.getLinkedAccount(linkedAccount.getUserId(), provider))
        .thenReturn(Optional.of(linkedAccount));
    when(accessTokenCacheDAO.getAccessTokenCacheEntry(linkedAccount)).thenReturn(Optional.empty());
    when(accessTokenCacheDAO.upsertAccessTokenCacheEntry(any()))
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
        new AuditLogEvent.Builder()
            .provider(provider)
            .userId(linkedAccount.getUserId())
            .clientIP(clientIP);
    String response =
        accessTokenCacheService.getLinkedAccountAccessToken(linkedAccount, auditLogEventBuilder);
    assertEquals(response, accessToken);
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
    var clientRegistration = TestUtils.createClientRegistration(linkedAccount.getProvider());

    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);
    when(accessTokenCacheDAO.getAccessTokenCacheEntry(linkedAccount)).thenReturn(Optional.empty());
    when(accessTokenCacheDAO.upsertAccessTokenCacheEntry(any()))
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
        new AuditLogEvent.Builder()
            .provider(provider)
            .userId(linkedAccount.getUserId())
            .clientIP(clientIP);
    String response =
        accessTokenCacheService.getLinkedAccountAccessToken(linkedAccount, auditLogEventBuilder);
    assertEquals(response, accessToken);
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
    var clientRegistration = TestUtils.createClientRegistration(linkedAccount.getProvider());
    var accessToken = UUID.randomUUID().toString();
    var tokenExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

    when(accessTokenCacheDAO.getAccessTokenCacheEntry(linkedAccount))
        .thenReturn(
            Optional.of(
                new AccessTokenCacheEntry.Builder()
                    .linkedAccountId(linkedAccount.getId().get())
                    .accessToken(accessToken)
                    .expiresAt(tokenExpiresAt)
                    .build()));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);
    when(accessTokenCacheDAO.upsertAccessTokenCacheEntry(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, AccessTokenCacheEntry.class));

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .provider(provider)
            .userId(linkedAccount.getUserId())
            .clientIP(clientIP);
    String response =
        accessTokenCacheService.getLinkedAccountAccessToken(linkedAccount, auditLogEventBuilder);
    assertEquals(response, accessToken);
    verify(auditLoggerMock, never()).logEvent(any());
  }

  @Test
  void testGetFenceProviderAccessTokenCacheExpired() {
    var provider = Provider.FENCE;
    var linkedAccount = TestUtils.createRandomLinkedAccount(provider).withId(random.nextInt());
    var clientRegistration = TestUtils.createClientRegistration(linkedAccount.getProvider());
    var accessToken = UUID.randomUUID().toString();
    var tokenExpiresAt = Instant.now().minus(1, ChronoUnit.HOURS);

    when(accessTokenCacheDAO.getAccessTokenCacheEntry(linkedAccount))
        .thenReturn(
            Optional.of(
                new AccessTokenCacheEntry.Builder()
                    .linkedAccountId(linkedAccount.getId().get())
                    .accessToken(accessToken)
                    .expiresAt(tokenExpiresAt)
                    .build()));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);
    when(accessTokenCacheDAO.upsertAccessTokenCacheEntry(any()))
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
        new AuditLogEvent.Builder()
            .provider(provider)
            .userId(linkedAccount.getUserId())
            .clientIP(clientIP);
    String response =
        accessTokenCacheService.getLinkedAccountAccessToken(linkedAccount, auditLogEventBuilder);
    assertEquals(response, accessToken);
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
}
