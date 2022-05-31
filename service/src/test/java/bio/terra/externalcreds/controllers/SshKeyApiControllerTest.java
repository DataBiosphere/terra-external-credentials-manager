package bio.terra.externalcreds.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.SamUserAuthenticatedRequest;
import bio.terra.common.iam.SamUserAuthenticatedRequestFactory;
import bio.terra.common.iam.TokenAuthenticatedRequest;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.SshKeyPairTestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SshKeyApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private SamUserAuthenticatedRequestFactory samUserAuthenticatedRequestFactoryMock;
  @MockBean private AuditLogger auditLoggerMock;

  @Captor ArgumentCaptor<AuditLogEvent> auditLogEventArgumentCaptor;

  @Test
  void getNonExistingSshKeyPair() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    mockSamUser(accessToken);
    var sshKeyType = "github";

    mvc.perform(
            get("/api/sshkeypair/v1/{type}", sshKeyType)
                .header("authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andReturn();

    verifyAuditLogEvent(sshKeyType, AuditLogEventType.GetSshKeyPairFailed);
  }

  @Test
  void deleteNonexistingSshKeyPair() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    mockSamUser(accessToken);
    var sshKeyPairType = "gitlab";

    mvc.perform(
            delete("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .header("authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andReturn();

    verifyAuditLogEvent(sshKeyPairType, AuditLogEventType.SshKeyPairDeletionFailed);
  }

  @Test
  void putGetAndDeleteSshKeyPair() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    String externalUserEmail =
        String.format("\"%s@gmail.com\"", RandomStringUtils.randomAlphabetic(5));
    mockSamUser(accessToken);
    var sshKeyPairType = "gitlab";
    var rsaEncodedKeyPair = SshKeyPairTestUtils.getRSAEncodedKeyPair(externalUserEmail);
    var sshKeyPair =
        new SshKeyPair()
            .privateKey(rsaEncodedKeyPair.getLeft())
            .publicKey(rsaEncodedKeyPair.getRight())
            .externalUserEmail(externalUserEmail);
    var requestBody = objectMapper.writeValueAsString(sshKeyPair);
    var putResult =
        mvc.perform(
                put("/api/sshkeypair/v1/{type}", sshKeyPairType)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .header("authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    verifyAuditLogEvent(sshKeyPairType, AuditLogEventType.PutSshKeyPair);
    assertEquals(
        sshKeyPair,
        objectMapper.readValue(putResult.getResponse().getContentAsByteArray(), SshKeyPair.class));

    var getResult =
        mvc.perform(
                get("/api/sshkeypair/v1/{type}", sshKeyPairType)
                    .header("authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        sshKeyPair,
        objectMapper.readValue(getResult.getResponse().getContentAsByteArray(), SshKeyPair.class));
    verifyAuditLogEvent(sshKeyPairType, AuditLogEventType.GetSshKeyPairSucceeded);

    mvc.perform(
            delete("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .header("authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andReturn();
    verifyAuditLogEvent(sshKeyPairType, AuditLogEventType.SshKeyPairDeleted);
  }

  @Test
  void generateGetAndDeleteSshKeyPair() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    mockSamUser(accessToken);
    String externalUserEmail =
        String.format("\"%s@gmail.com\"", RandomStringUtils.randomAlphabetic(5));
    var sshKeyPairType = "gitlab";
    var postResult =
        mvc.perform(
                post("/api/sshkeypair/v1/{type}", sshKeyPairType)
                    .header("authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(externalUserEmail))
            .andExpect(status().isOk())
            .andReturn();

    var generatedSshKeyPair =
        objectMapper.readValue(postResult.getResponse().getContentAsByteArray(), SshKeyPair.class);
    verifyAuditLogEvent(sshKeyPairType, AuditLogEventType.SshKeyPairCreated);

    var getResult =
        mvc.perform(
                get("/api/sshkeypair/v1/{type}", sshKeyPairType)
                    .header("authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        generatedSshKeyPair,
        objectMapper.readValue(getResult.getResponse().getContentAsByteArray(), SshKeyPair.class));
    verifyAuditLogEvent(sshKeyPairType, AuditLogEventType.GetSshKeyPairSucceeded);

    mvc.perform(
            delete("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .header("authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andReturn();
    verifyAuditLogEvent(sshKeyPairType, AuditLogEventType.SshKeyPairDeleted);
  }

  @Test
  void generateWithInvalidInput() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    mockSamUser(accessToken);
    String externalUserEmail = String.format("%s@gmail.com", RandomStringUtils.randomAlphabetic(5));
    var sshKeyPairType = "gitlab";
    mvc.perform(
            post("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .header("authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(externalUserEmail))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSshKeyPairWithInvalidKeyType() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    mockSamUser(accessToken);
    var invalidSshKeyType = "azures";
    mvc.perform(
            get("/api/sshkeypair/v1/{type}", invalidSshKeyType)
                .header("authorization", "Bearer " + accessToken))
        .andExpect(status().isBadRequest());
  }

  private void mockSamUser(String accessToken) {
    when(samUserAuthenticatedRequestFactoryMock.from(
            any(HttpServletRequest.class), any(ApiClient.class)))
        .thenReturn(
            SamUserAuthenticatedRequest.builder()
                .setEmail("email")
                .setTokenRequest(TokenAuthenticatedRequest.builder().setToken(accessToken).build())
                .setSubjectId(UUID.randomUUID().toString())
                .build());
  }

  private void verifyAuditLogEvent(String sshKeyPairType, AuditLogEventType auditLogEventType) {
    verify(auditLoggerMock).logEvent(auditLogEventArgumentCaptor.capture());
    var auditLogEvent = auditLogEventArgumentCaptor.getValue();
    assertEquals(sshKeyPairType, auditLogEvent.getSshKeyPairType().get().toLowerCase());
    assertEquals(auditLogEventType, auditLogEvent.getAuditLogEventType());

    Mockito.clearInvocations(auditLoggerMock);
  }
}
