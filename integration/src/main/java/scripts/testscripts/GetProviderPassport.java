package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.externalcreds.api.OidcApi;
import bio.terra.externalcreds.model.Provider;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import scripts.utils.ClientTestUtils;

@Slf4j
public class GetProviderPassport extends TestScript {
  private String provider;
  private OidcApi oidcApi;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    // TODO: do we want to loop through this list in case we want multiple test users in the future?
    var apiClient = ClientTestUtils.getClientWithTestUserAuth(testUsers.get(0), server);
    oidcApi = new OidcApi(apiClient);
    provider = oidcApi.listProviders().get(0);
    if (provider == null) throw new Exception("No provider found.");
  }

  @Override
  public void userJourney(TestUserSpecification testUser) {
    log.info("Checking the get provider passport endpoint.");

    // TODO: update GetProviderPassport.json in perf to run 120 tests per second
    // check the response code
    var passportResponse = oidcApi.getProviderPassportWithHttpInfo(Provider.fromValue(provider));
    var httpCode = passportResponse.getStatusCode();

    assertEquals(HttpStatus.OK, httpCode);
    log.info("Get provider passport return code: {}", httpCode);

    // check the response body
    assertNotNull(provider);
    assertNotNull(passportResponse.getBody());
  }
}
