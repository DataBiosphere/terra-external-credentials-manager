package bio.terra.externalcreds.services;

import static org.mockito.Mockito.verify;

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

public class TokenProviderServiceTest extends BaseTest {

  @Autowired private TokenProviderService tokenProviderService;
  @MockBean private AuditLogger auditLoggerMock;
  private final String providerName = Provider.GITHUB.toString();
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

  // test creation of post request to github
  // test access token request is logged on success and failure

  // can't really test github itself
  // test selects correct refresh token? already covered
  // test failure if no refresh token? already covered
  // test failure if no linked account found? no that's already covered

  @Test
  void testCreateProviderAccessTokenRequest() {
    tokenProviderService.getProviderAccessToken(providerName, userId);
  }

  @Test
  void testLogProviderAccessTokenResponse() {
    tokenProviderService.getProviderAccessToken(providerName, userId);
  }
}
