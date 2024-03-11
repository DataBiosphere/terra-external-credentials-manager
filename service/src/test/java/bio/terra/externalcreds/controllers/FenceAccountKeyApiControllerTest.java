package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.LinkedAccount.Builder;
import bio.terra.externalcreds.services.FenceAccountKeyService;
import bio.terra.externalcreds.services.LinkedAccountService;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class FenceAccountKeyApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private LinkedAccountService linkedAccountServiceMock;

  @MockBean private SamUserFactory samUserFactoryMock;
  @MockBean private FenceAccountKeyService fenceAccountKeyServiceMock;
  @MockBean private AuditLogger auditLoggerMock;
  private Provider provider = Provider.RAS; // TO-Do: Change to fence token provider

  @Nested
  class GetServiceAccountKey {

    @Test
    void testGetServiceAccountKey() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var externalUserId = UUID.randomUUID().toString();
      var fenceAccountKey =
          TestUtils.createRandomFenceAccountKey()
              .withExpiresAt(new Timestamp(System.currentTimeMillis() + 1000).toInstant());

      mockSamUser(userId, accessToken);

      when(linkedAccountServiceMock.getLinkedAccount(userId, provider))
          .thenReturn(
              Optional.of(
                  new Builder()
                      .provider(provider)
                      .userId(userId)
                      .externalUserId(externalUserId)
                      .refreshToken("")
                      .expires(new Timestamp(0))
                      .isAuthenticated(true)
                      .build()));
      when(fenceAccountKeyServiceMock.getFenceAccountKey(userId, provider))
          .thenReturn(Optional.of(fenceAccountKey));

      mvc.perform(
              get("/api/fenceAccountKey/v1/{provider}/key", provider)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk())
          .andExpect(content().string(fenceAccountKey.getKeyJson()));

      // check that a log was recorded
      verify(auditLoggerMock)
          .logEvent(
              new AuditLogEvent.Builder()
                  .auditLogEventType(AuditLogEventType.GetServiceAccountKey)
                  .provider(provider)
                  .userId(userId)
                  .clientIP("127.0.0.1")
                  .externalUserId(externalUserId)
                  .build());
    }

    @Test
    void testGetServiceAccountKeyDoesNotReturnExpired() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      var fenceAccountKey =
          TestUtils.createRandomFenceAccountKey()
              .withExpiresAt(new Timestamp(System.currentTimeMillis() - 1000).toInstant());
      mockSamUser(userId, accessToken);
      when(fenceAccountKeyServiceMock.getFenceAccountKey(userId, provider))
          .thenReturn(Optional.of(fenceAccountKey));
      mvc.perform(
              get("/api/fenceAccountKey/v1/{provider}/key", provider)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }

    @Test
    void testGetServiceAccountKey404() throws Exception {
      var accessToken = "testToken";
      var userId = UUID.randomUUID().toString();
      mockSamUser(userId, accessToken);
      mvc.perform(
              get("/api/fenceAccountKey/v1/{provider}/key", provider)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }
  }

  private void mockSamUser(String userId, String accessToken) {
    when(samUserFactoryMock.from(any(HttpServletRequest.class), any(String.class)))
        .thenReturn(new SamUser("email", userId, new BearerToken(accessToken)));
  }
}
