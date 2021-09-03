package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.api.OidcApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import lombok.extern.slf4j.Slf4j;
import scripts.utils.ClientTestUtils;

@Slf4j
public class GetLink404ForAuthenticatedUser extends TestScript {

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    var apiClient = ClientTestUtils.getClientWithAuthenticatedUser(testUser, server);
    var oidcApi = new OidcApi(apiClient);

    oidcApi.getLink("testProvider");

    // assert that the user was authenticated through sam, but no linked account exists for them
    var httpCode = oidcApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, httpCode);
    log.info("Service status return code: {}", httpCode);
  }
}
