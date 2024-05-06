package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEvent.Builder;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.core.*;

public class TokenProviderServiceTest extends BaseTest {

  @Autowired private TokenProviderService tokenProviderService;
  @MockBean private AuditLogger auditLoggerMock;
  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private ProviderTokenClientCache providerTokenClientCacheMock;
  @MockBean private OAuth2Service oAuth2ServiceMock;

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
  void testGetProviderAccessTokenExpiredLinkedAccount() {
    var userId = "fakeUserId";
    var provider = Provider.GITHUB;

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.GetProviderAccessToken)
            .provider(provider)
            .userId(userId)
            .externalUserId(Optional.empty())
            .clientIP(clientIP);

    var expiredLinkedAccount =
        TestUtils.createRandomLinkedAccount(provider)
            .withExpires(Timestamp.from(Instant.now().minusSeconds(60)));
    when(linkedAccountService.getLinkedAccount(userId, provider))
        .thenReturn(Optional.of(expiredLinkedAccount));

    assertThrows(
        ForbiddenException.class,
        () -> tokenProviderService.getProviderAccessToken(userId, provider, auditLogEventBuilder));
  }

  @Test
  void testGetProviderAccessTokenUnauthorized() {
    var linkedAccount = TestUtils.createRandomLinkedAccount(provider);
    var clientRegistration = TestUtils.createClientRegistration(linkedAccount.getProvider());

    when(linkedAccountService.getLinkedAccount(linkedAccount.getUserId(), provider))
        .thenReturn(Optional.of(linkedAccount));
    when(providerTokenClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(clientRegistration);
    when(oAuth2ServiceMock.authorizeWithRefreshToken(
            eq(clientRegistration),
            eq(new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null)),
            any(Set.class)))
        .thenThrow(
            new OAuth2AuthorizationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN)));

    assertThrows(
        OAuth2AuthorizationException.class,
        () ->
            tokenProviderService.getProviderAccessToken(
                linkedAccount.getUserId(), provider, auditLogEventBuilder));
  }
}
