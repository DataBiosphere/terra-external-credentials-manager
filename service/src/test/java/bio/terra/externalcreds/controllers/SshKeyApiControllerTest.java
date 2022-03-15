package bio.terra.externalcreds.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.services.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.Test;
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

  private static final String DEFAULT_USER_ID = "foo";
  private static final String DEFAULT_ACCESS_TOKEN = "foo_access_token";
  private static final String EXTERNAL_USER_EMAIL = "foo@monkeyseesmonkeydo.com";

  @Test
  void getNonExistingSshKeyPair() throws Exception {
    mockSamUser();
    var sshKeyType = "github";

    mvc.perform(
            get("/api/sshkeypair/v1/{type}", sshKeyType)
                .header("authorization", "Bearer " + DEFAULT_ACCESS_TOKEN))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  void putGetAndDeleteSshKeyPair() throws Exception {
    mockSamUser();
    var sshKeyPairType = "gitlab";
    var rsaEncodedKeyPair = TestUtils.getRSAEncodedKeyPair(EXTERNAL_USER_EMAIL);
    var sshKeyPair =
        new SshKeyPair()
            .privateKey(rsaEncodedKeyPair.getLeft())
            .publicKey(rsaEncodedKeyPair.getRight())
            .externalUserEmail(EXTERNAL_USER_EMAIL);
    var requestBody = objectMapper.writeValueAsString(sshKeyPair);
    var putResult =
        mvc.perform(
                put("/api/sshkeypair/v1/{type}", sshKeyPairType)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .header("authorization", "Bearer " + DEFAULT_ACCESS_TOKEN))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        sshKeyPair,
        objectMapper.readValue(putResult.getResponse().getContentAsByteArray(), SshKeyPair.class));

    var getResult =
        mvc.perform(
                get("/api/sshkeypair/v1/{type}", sshKeyPairType)
                    .header("authorization", "Bearer " + DEFAULT_ACCESS_TOKEN))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        sshKeyPair,
        objectMapper.readValue(getResult.getResponse().getContentAsByteArray(), SshKeyPair.class));

    mvc.perform(
            delete("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .header("authorization", "Bearer " + DEFAULT_ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  void getSshKeyPairWithInvalidKeyType() throws Exception {
    mockSamUser();
    var invalidSshKeyType = "azures";
    mvc.perform(
            get("/api/sshkeypair/v1/{type}", invalidSshKeyType)
                .header("authorization", "Bearer " + DEFAULT_ACCESS_TOKEN))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("Invalid SSH key pair type")));
  }

  @Test
  void samThrowsApiException() throws Exception {
    var usersApiMock = mock(UsersApi.class);
    when(samServiceMock.samUsersApi(DEFAULT_ACCESS_TOKEN)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo()).thenThrow(new ApiException());

    var sshKeyPairType = "gitlab";
    var rsaEncodedKeyPair = TestUtils.getRSAEncodedKeyPair(EXTERNAL_USER_EMAIL);
    var sshKeyPair =
        new SshKeyPair()
            .privateKey(rsaEncodedKeyPair.getLeft())
            .publicKey(rsaEncodedKeyPair.getRight())
            .externalUserEmail(EXTERNAL_USER_EMAIL);
    var requestBody = objectMapper.writeValueAsString(sshKeyPair);
    mvc.perform(
            put("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("authorization", "Bearer " + DEFAULT_ACCESS_TOKEN))
        .andExpect(status().is5xxServerError());
  }

  @Test
  void samThrowsNotFoundException() throws Exception {
    var usersApiMock = mock(UsersApi.class);
    when(samServiceMock.samUsersApi(DEFAULT_ACCESS_TOKEN)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo())
        .thenThrow(new ApiException(HttpStatus.SC_NOT_FOUND, "user status info not found"));

    var sshKeyPairType = "gitlab";
    var rsaEncodedKeyPair = TestUtils.getRSAEncodedKeyPair(EXTERNAL_USER_EMAIL);
    var sshKeyPair =
        new SshKeyPair()
            .privateKey(rsaEncodedKeyPair.getLeft())
            .publicKey(rsaEncodedKeyPair.getRight())
            .externalUserEmail(EXTERNAL_USER_EMAIL);
    var requestBody = objectMapper.writeValueAsString(sshKeyPair);
    mvc.perform(
            put("/api/sshkeypair/v1/{type}", sshKeyPairType)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("authorization", "Bearer " + DEFAULT_ACCESS_TOKEN))
        .andExpect(status().isForbidden());
  }

  private void mockSamUser() throws Exception {
    var usersApiMock = mock(UsersApi.class);
    var userStatusInfo = new UserStatusInfo().userSubjectId(DEFAULT_USER_ID);
    when(samServiceMock.samUsersApi(DEFAULT_ACCESS_TOKEN)).thenReturn(usersApiMock);
    when(usersApiMock.getUserStatusInfo()).thenReturn(userStatusInfo);
  }
}
