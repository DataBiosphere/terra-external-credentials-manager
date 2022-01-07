package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.externalcreds.api.OidcApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import lombok.extern.slf4j.Slf4j;
import scripts.utils.ClientTestUtils;

@Slf4j
public class ListProviders extends TestScript {

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the list providers endpoint.");
    var apiClient = ClientTestUtils.getClientWithoutAccessToken(server);
    var oidcApi = new OidcApi(apiClient);

    var providers = oidcApi.listProviders();

    // check the response code
    var httpCode = oidcApi.getApiClient().getStatusCode();
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpCode);
    log.info("List providers return code: {}", httpCode);

    // check the response body
    assertNotNull(providers);
  }
}
