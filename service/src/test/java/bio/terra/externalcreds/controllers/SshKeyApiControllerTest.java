package bio.terra.externalcreds.controllers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.ErrorReport;
import bio.terra.externalcreds.generated.model.SshKeyInfo;
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
  void getSshKey_throws500() throws Exception {
    var sshKeyType = "GITHUB";
    MvcResult failedResult =
        mvc.perform(get("/api/sshkey/v1/{sshkey_type}", sshKeyType))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void storeSshKey_throws500() throws Exception {
    var sshKeyType = "GITHUB";
    SshKeyInfo sshkeyInfo = new SshKeyInfo();

    var sshKey =
        "-----BEGIN OPENSSH PRIVATE KEY-----\n"
            + "abcde12345/+xXXXYZ//890=\n"
            + "-----END OPENSSH PRIVATE KEY-----";
    sshkeyInfo.key(sshKey.getBytes());
    sshkeyInfo.externalUserEmail("yuhuyoyo@google.com");
    String requestBody = objectMapper.writeValueAsString(sshkeyInfo);

    MvcResult failedResult =
        mvc.perform(
                put("/api/sshkey/v1/{sshkey_type}", sshKeyType)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .content(requestBody))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void deleteSshKey_throws500() throws Exception {
    var sshKeyType = "GITLAB";

    MvcResult failedResult =
        mvc.perform(delete("/api/sshkey/v1/{sshkey_type}", sshKeyType))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void updateSshKey_invalidProvider_throwsBadRequest() throws Exception {
    var invalidSshKeyType = "AZURES";
    MvcResult failedResult =
        mvc.perform(get("/api/sshkey/v1/{sshkey_type}", invalidSshKeyType))
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
