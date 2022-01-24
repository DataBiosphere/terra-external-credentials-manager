package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import lombok.extern.slf4j.Slf4j;
import scripts.utils.ClientTestUtils;

@Slf4j
public class GetProviderPassport extends TestScript {

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the get provider passport endpoint.");
    var apiClient = ClientTestUtils.getClientWithTestUserAuth(testUser, server);
    var oidcApi = new OidcApi(apiClient);

    var providers = oidcApi.listProviders();

    // TODO: put actual test here
    // check the response code
    var httpCode = oidcApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpCode);
    log.info("List providers return code: {}", httpCode);

    // check the response body
    assertNotNull(providers);
  }
}
