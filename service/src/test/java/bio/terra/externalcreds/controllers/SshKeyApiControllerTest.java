package bio.terra.externalcreds.controllers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.ErrorReport;
import bio.terra.externalcreds.generated.model.SshKeyPairInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class SshKeyApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void getSshKeyPair_throws500() throws Exception {
    var sshKeyType = "GITHUB";
    MvcResult failedResult =
        mvc.perform(get("/api/sshkeypair/v1/{sshkey_type}", sshKeyType))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void storeSshKeyPair_throws500() throws Exception {
    var sshKeyType = "GITHUB";
    SshKeyPairInfo sshKeyPairInfo = new SshKeyPairInfo();
    var sshPrivateKey =
        "-----BEGIN OPENSSH PRIVATE KEY-----\n"
            + "abcde12345/+xXXXYZ//890=\n"
            + "-----END OPENSSH PRIVATE KEY-----";
    var sshPublicKey = "ssh-ed25519 AAABBBccc123 yuhuyoyo@google.com";
    sshKeyPairInfo.privateKey(sshPrivateKey.getBytes());
    sshKeyPairInfo.publicKey(sshPublicKey.getBytes());
    sshKeyPairInfo.externalUserEmail("yuhuyoyo@google.com");
    String requestBody = objectMapper.writeValueAsString(sshKeyPairInfo);

    MvcResult failedResult =
        mvc.perform(
                put("/api/sshkeypair/v1/{sshkey_type}", sshKeyType)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .content(requestBody))
            .andExpect(status().is(500))
            .andReturn();

    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void deleteSshKeyPair_throws500() throws Exception {
    var sshKeyType = "GITLAB";
    MvcResult failedResult =
        mvc.perform(delete("/api/sshkeypair/v1/{sshkey_type}", sshKeyType))
            .andExpect(status().is(500))
            .andReturn();

    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void updateSshKeyPair_invalidProvider_throwsBadRequest() throws Exception {
    var invalidSshKeyType = "AZURES";
    MvcResult failedResult =
        mvc.perform(get("/api/sshkeypair/v1/{sshkey_type}", invalidSshKeyType))
            .andExpect(status().isBadRequest())
            .andReturn();

    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(
        requestError.getMessage(),
        Matchers.containsString(
            "Request could not be parsed or was invalid: MethodArgumentTypeMismatchException. Ensure that all types are correct and that enums have valid values."));
  }
}
