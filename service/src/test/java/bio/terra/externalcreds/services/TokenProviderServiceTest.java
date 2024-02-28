package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import bio.terra.externalcreds.models.LinkedAccount;
import java.util.Optional;
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
  @MockBean private ProviderTokenClientCache providerTokenClientCacheMock;
  @MockBean private OAuth2Service oAuth2ServiceMock;

  private final Provider provider = Provider.GITHUB;
  private final String providerName = provider.toString();
  private final String userId = UUID.randomUUID().toString();
  private final String clientIP = "127.0.0.1";
  private final AuditLogEvent.Builder auditLogEventBuilder =
      new Builder().providerName(providerName).userId(userId).clientIP(clientIP);

  @Test
  void testLogLinkCreateSuccess() {
    Optional<LinkedAccount> linkedAccount =
        Optional.ofNullable(TestUtils.createRandomLinkedAccount(providerName));
    tokenProviderService.logLinkCreation(linkedAccount, auditLogEventBuilder);
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.LinkCreated)
                .providerName(providerName)
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
                .providerName(providerName)
                .userId(userId)
                .externalUserId(Optional.empty())
                .clientIP(clientIP)
                .build());
  }

  @Test
  void testGetProviderAccessTokenSuccess() {
    var linkedAccount = TestUtils.createRandomLinkedAccount(providerName);
    var clientRegistration = createClientRegistration(linkedAccount.getProviderName());

    when(linkedAccountService.getLinkedAccount(linkedAccount.getUserId(), provider.toString()))
        .thenReturn(Optional.of(linkedAccount));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProviderName()))
        .thenReturn(Optional.of(clientRegistration));

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
        new Builder()
            .providerName(providerName)
            .userId(linkedAccount.getUserId())
            .clientIP(clientIP);
    Optional<String> response =
        tokenProviderService.getProviderAccessToken(
            linkedAccount.getUserId(), provider, auditLogEventBuilder);
    assertEquals(response.get(), accessToken);
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.GetProviderAccessToken)
                .providerName(providerName)
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
            .providerName(providerName)
            .userId(userId)
            .externalUserId(Optional.empty())
            .clientIP(clientIP);

    when(linkedAccountService.getLinkedAccount(userId, provider.toString()))
        .thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> tokenProviderService.getProviderAccessToken(userId, provider, auditLogEventBuilder));
  }

  @Test
  void testGetProviderAccessTokenUnauthorized() {
    var linkedAccount = TestUtils.createRandomLinkedAccount(providerName);
    var clientRegistration = createClientRegistration(linkedAccount.getProviderName());

    when(linkedAccountService.getLinkedAccount(linkedAccount.getUserId(), provider.toString()))
        .thenReturn(Optional.of(linkedAccount));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProviderName()))
        .thenReturn(Optional.of(clientRegistration));
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

  private ClientRegistration createClientRegistration(String providerName) {
    return ClientRegistration.withRegistrationId(providerName)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .build();
  }
}
