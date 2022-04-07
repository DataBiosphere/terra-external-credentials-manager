package bio.terra.externalcreds.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.services.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
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

  @MockBean private SamService samServiceMock;
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
    String externalUserEmail = String.format("%s@gmail.com", RandomStringUtils.randomAlphabetic(5));
    mockSamUser(accessToken);
    var sshKeyPairType = "gitlab";
    var rsaEncodedKeyPair = TestUtils.getRSAEncodedKeyPair(externalUserEmail);
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
    String externalUserEmail = String.format("%s@gmail.com", RandomStringUtils.randomAlphabetic(5));
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
  void getSshKeyPairWithInvalidKeyType() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    mockSamUser(accessToken);
    var invalidSshKeyType = "azures";
    mvc.perform(
            get("/api/sshkeypair/v1/{type}", invalidSshKeyType)
                .header("authorization", "Bearer " + accessToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void samThrowsApiException() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    String externalUserEmail = String.format("%s@gmail.com", RandomStringUtils.randomAlphabetic(5));
    var usersApiMock = mock(UsersApi.class);
    when(samServiceMock.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo()).thenThrow(new ApiException());

    var sshKeyPairType = "gitlab";
    mvc.perform(
            post("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .header("authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(externalUserEmail))
        .andExpect(status().is5xxServerError());
  }

  @Test
  void samThrowsNotFoundException() throws Exception {
    String accessToken = RandomStringUtils.randomAlphanumeric(10);
    String externalUserEmail = String.format("%s@gmail.com", RandomStringUtils.randomAlphabetic(5));
    var usersApiMock = mock(UsersApi.class);
    when(samServiceMock.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo())
        .thenThrow(new ApiException(HttpStatus.SC_NOT_FOUND, "user status info not found"));

    var sshKeyPairType = "gitlab";
    mvc.perform(
            post("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .header("authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(externalUserEmail))
        .andExpect(status().isForbidden());
  }

  private void mockSamUser(String accessToken) throws Exception {
    var usersApiMock = mock(UsersApi.class);
    var userStatusInfo = new UserStatusInfo().userSubjectId(UUID.randomUUID().toString());
    when(samServiceMock.samUsersApi(accessToken)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo()).thenReturn(userStatusInfo);
  }

  private void verifyAuditLogEvent(String sshKeyPairType, AuditLogEventType auditLogEventType) {
    verify(auditLoggerMock).logEvent(auditLogEventArgumentCaptor.capture());
    var auditLogEvent = auditLogEventArgumentCaptor.getValue();
    assertEquals(sshKeyPairType, auditLogEvent.getSshKeyPairType().get().toLowerCase());
    assertEquals(auditLogEventType, auditLogEvent.getAuditLogEventType());

    Mockito.clearInvocations(auditLoggerMock);
  }
}
