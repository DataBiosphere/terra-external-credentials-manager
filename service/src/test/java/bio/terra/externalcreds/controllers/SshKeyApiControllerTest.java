package bio.terra.externalcreds.controllers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.ErrorReport;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SshKeyApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void getSshKeyPair_throws500() throws Exception {
    var sshKeyType = "GITHUB";
    var failedResult =
        mvc.perform(get("/api/sshkeypair/v1/{type}", sshKeyType))
            .andExpect(status().is5xxServerError())
            .andReturn();
    var requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void putSshKeyPair_throws500() throws Exception {
    var sshKeyPairType = SshKeyPairType.GITHUB;
    var sshPrivateKey =
        "-----BEGIN OPENSSH PRIVATE KEY-----\n"
            + "abcde12345/+xXXXYZ//890=\n"
            + "-----END OPENSSH PRIVATE KEY-----";
    var sshPublicKey = "ssh-ed25519 AAABBBccc123 yuhuyoyo@google.com";
    var sshKeyPair =
        new SshKeyPair()
            .privateKey(sshPrivateKey)
            .publicKey(sshPublicKey)
            .externalUserEmail("yuhuyoyo@google.com");
    var requestBody = objectMapper.writeValueAsString(sshKeyPair);

    var failedResult =
        mvc.perform(
                put("/api/sshkeypair/v1/{type}", sshKeyPairType)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .content(requestBody))
            .andExpect(status().is5xxServerError())
            .andReturn();

    var requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void deleteSshKeyPair_throws500() throws Exception {
    var sshKeyType = "GITLAB";
    var failedResult =
        mvc.perform(delete("/api/sshkeypair/v1/{type}", sshKeyType))
            .andExpect(status().is5xxServerError())
            .andReturn();

    var requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void updateSshKeyPair_invalidProvider_throwsBadRequest() throws Exception {
    var invalidSshKeyType = "AZURES";
    var failedResult =
        mvc.perform(get("/api/sshkeypair/v1/{type}", invalidSshKeyType))
            .andExpect(status().isBadRequest())
            .andReturn();

    var requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(
        requestError.getMessage(),
        Matchers.containsString(
            "Request could not be parsed or was invalid: MethodArgumentTypeMismatchException. Ensure that all types are correct and that enums have valid values."));
  }
}
