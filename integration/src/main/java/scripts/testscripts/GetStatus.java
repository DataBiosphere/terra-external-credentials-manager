package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import scripts.utils.ClientTestUtils;

@Slf4j
public class GetStatus extends TestScript {

  @Override
  public void userJourney(TestUserSpecification testUser) {
    log.info("Checking the service status endpoint.");
    var apiClient = ClientTestUtils.getClientWithoutAuth(server);
    var publicApi = new PublicApi(apiClient);

    var httpCode = publicApi.getStatusWithHttpInfo().getStatusCode();
    assertEquals(HttpStatus.OK, httpCode);
    log.info("Service status return code: {}", httpCode);
  }
}
