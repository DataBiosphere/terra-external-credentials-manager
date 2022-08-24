package bio.terra.externalcreds.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.VersionProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@Tag("unit")
@AutoConfigureMockMvc
public class PublicApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private ExternalCredsConfig externalCredsConfig;

  @Test
  void testGetStatus() throws Exception {
    mvc.perform(get("/status"))
        .andExpect(content().json("""
            {"ok": true,"systems": { "postgres": true }}"""));
  }

  @Test
  void testGetVersion() throws Exception {
    var versionProperties =
        VersionProperties.create()
            .setGitTag("gitTag")
            .setGitHash("gitHash")
            .setBuild("build")
            .setGithub("github");
    when(externalCredsConfig.getVersion()).thenReturn(versionProperties);

    mvc.perform(get("/version"))
        .andExpect(
            content()
                .json(
                    """
                        {"gitTag": "gitTag", "gitHash": "gitHash", "github": "github", "build": "build"}"""));
  }
}
