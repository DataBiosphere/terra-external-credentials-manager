package bio.terra.externalcreds.controllers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.ErrorReport;
import bio.terra.externalcreds.generated.model.StoreSshKeyRequest;
import bio.terra.externalcreds.generated.model.UpdateSshKeyRequestBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
public class SshKeyApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void getSshKey_throws500() throws Exception {
    var providerName = "GITHUB";
    MvcResult failedResult =
        mvc.perform(get("/api/secrets/sshkeys/v1/{provider}", providerName))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void storeSshKey_throws500() throws Exception {
    var providerName = "GITHUB";
    StoreSshKeyRequest storeSshKeyRequest = new StoreSshKeyRequest();

    var sshKey =
        "-----BEGIN OPENSSH PRIVATE KEY-----\n"
            + "abcde12345/+xXXXYZ//890=\n"
            + "-----END OPENSSH PRIVATE KEY-----";
    storeSshKeyRequest.key(sshKey.getBytes());
    String requestBody = objectMapper.writeValueAsString(storeSshKeyRequest);

    MvcResult failedResult =
        mvc.perform(
                post("/api/secrets/sshkeys/v1/{provider}", providerName)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .content(requestBody))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void deleteSshKey() throws Exception {
    var providerName = "GITLAB";

    MvcResult failedResult =
        mvc.perform(delete("/api/secrets/sshkeys/v1/{provider}", providerName))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void updateSshKey_throws500() throws Exception {
    var providerName = "AZURE";
    UpdateSshKeyRequestBody updateSshKeyRequestBody = new UpdateSshKeyRequestBody();
    updateSshKeyRequestBody.name("new_name");
    updateSshKeyRequestBody.description("new description");

    MvcResult failedResult =
        mvc.perform(
                patch("/api/secrets/sshkeys/v1/{provider}", providerName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateSshKeyRequestBody)))
            .andExpect(status().is(500))
            .andReturn();
    ErrorReport requestError =
        objectMapper.readValue(failedResult.getResponse().getContentAsString(), ErrorReport.class);
    assertThat(requestError.getMessage(), Matchers.containsString("Not implemented"));
  }

  @Test
  void updateSshKey_invalidProvider_throwsBadRequest() throws Exception {
    var invalidProvider = "AZURES";
    UpdateSshKeyRequestBody updateSshKeyRequestBody = new UpdateSshKeyRequestBody();
    updateSshKeyRequestBody.name("new_name");
    updateSshKeyRequestBody.description("new description");

    MvcResult failedResult =
        mvc.perform(
                patch("/api/secrets/sshkeys/v1/{provider}", invalidProvider)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateSshKeyRequestBody)))
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
