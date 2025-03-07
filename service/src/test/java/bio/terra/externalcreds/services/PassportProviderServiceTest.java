package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public class PassportProviderServiceTest extends BaseTest {
  @Autowired private PassportProviderService passportProviderService;
  @MockitoBean private AuditLogger auditLoggerMock;
  @MockitoBean private LinkedAccountService linkedAccountService;
  @MockitoBean private ProviderTokenClientCache providerTokenClientCacheMock;
  @MockitoBean private OAuth2Service oAuth2ServiceMock;
  @MockitoBean private JwtUtils jwtUtilsMock;

  private final Provider provider = Provider.GITHUB;
  private final String userId = UUID.randomUUID().toString();
  private final String clientIP = "127.0.0.1";
  private final AuditLogEvent.Builder auditLogEventBuilder =
      new Builder().provider(provider).userId(userId).clientIP(clientIP);

  // TODO CORE-332: different tests for passport vs. non-passport logging?
  @Test
  void testLogLinkCreateSuccess() {
    when(jwtUtilsMock.getJwtTransactionClaim(anyString()))
        .thenReturn(Optional.of("unit-test-claim"));

    LinkedAccount linkedAccount = TestUtils.createRandomLinkedAccount(provider);
    LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas =
        new LinkedAccountWithPassportAndVisas.Builder()
            .linkedAccount(linkedAccount)
            .passport(TestUtils.createRandomPassport())
            .build();

    passportProviderService.logLinkCreation(
        Optional.of(linkedAccountWithPassportAndVisas), auditLogEventBuilder);
    verify(auditLoggerMock)
        .logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.LinkCreated)
                .provider(provider)
                .userId(userId)
                .transactionClaim("unit-test-claim")
                .externalUserId(linkedAccount.getExternalUserId())
                .clientIP(clientIP)
                .build());
  }

  @Test
  void testLogLinkCreateFailure() {
    passportProviderService.logLinkCreation(Optional.empty(), auditLogEventBuilder);
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
        () ->
            passportProviderService.getProviderAccessToken(userId, provider, auditLogEventBuilder));
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
        () ->
            passportProviderService.getProviderAccessToken(userId, provider, auditLogEventBuilder));
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
            passportProviderService.getProviderAccessToken(
                linkedAccount.getUserId(), provider, auditLogEventBuilder));
  }
}
