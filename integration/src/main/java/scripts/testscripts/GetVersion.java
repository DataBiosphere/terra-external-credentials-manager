package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.externalcreds.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import scripts.utils.ClientTestUtils;

@Slf4j
public class GetVersion extends TestScript {

  @Override
  public void userJourney(TestUserSpecification testUser) {
    log.info("Checking the version endpoint.");
    var apiClient = ClientTestUtils.getClientWithoutAuth(server);
    var publicApi = new PublicApi(apiClient);

    var versionResponse = publicApi.getVersionWithHttpInfo();
    var versionProperties = versionResponse.getBody();

    // check the response code
    var httpCode = versionResponse.getStatusCode();
    assertEquals(HttpStatus.OK, httpCode);
    log.info("Service status return code: {}", httpCode);

    // check the response body
    assertNotNull(versionProperties);
    assertNotNull(versionProperties.getGitHash());
    assertNotNull(versionProperties.getGitTag());
    assertNotNull(versionProperties.getGithub());
    assertNotNull(versionProperties.getBuild());
  }
}
