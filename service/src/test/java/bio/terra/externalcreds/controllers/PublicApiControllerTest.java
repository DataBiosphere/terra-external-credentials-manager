package bio.terra.externalcreds.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.VersionProperties;
import bio.terra.externalcreds.generated.model.SubsystemStatusDetail;
import bio.terra.externalcreds.generated.model.SystemStatus;
import bio.terra.externalcreds.generated.model.SystemStatusDetail;
import bio.terra.externalcreds.services.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class PublicApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private ExternalCredsConfig externalCredsConfig;
  @MockBean private StatusService statusService;

  @Test
  void testGetStatus() throws Exception {
    when(statusService.getSystemStatus())
        .thenReturn(new SystemStatus().ok(true).putSystemsItem("postgres", true));
    mvc.perform(get("/status"))
        .andExpect(content().json("""
            {"ok": true,"systems": { "postgres": true }}"""));
  }

  @Test
  void testGetStatusDetail() throws Exception {
    when(statusService.getSystemStatusDetail())
        .thenReturn(
            new SystemStatusDetail()
                .ok(true)
                .addSystemsItem(new SubsystemStatusDetail().name("postgres").ok(true))
                .addSystemsItem(new SubsystemStatusDetail().name("sam").ok(true))
                .addSystemsItem(new SubsystemStatusDetail().name("github").ok(true)));
    mvc.perform(get("/api/status/v1"))
        .andExpect(
            content()
                .json(
                    """
            {
              "ok": true,
              "systems": [
                { "name": "postgres", "ok": true},
                { "name": "sam", "ok": true},
                { "name": "github", "ok": true}
               ]
            }"""));
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
