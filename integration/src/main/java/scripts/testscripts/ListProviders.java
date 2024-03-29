package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.externalcreds.api.OidcApi;
import bio.terra.externalcreds.model.Provider;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import scripts.utils.ClientTestUtils;

@Slf4j
public class ListProviders extends TestScript {

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    log.info("Checking the list providers endpoint.");
    var apiClient = ClientTestUtils.getClientWithTestUserAuth(testUser, server);
    var oidcApi = new OidcApi(apiClient);

    var providersResponse = oidcApi.listProvidersWithHttpInfo();

    // check the response code
    var httpCode = providersResponse.getStatusCode();
    assertEquals(HttpStatus.OK, httpCode);
    log.info("List providers return code: {}", httpCode);

    // check the response body
    assertNotNull(providersResponse.getBody());
    var expected =
        Arrays.stream(Provider.values()).map(Provider::toString).collect(Collectors.toSet());
    var actual = new HashSet<>(providersResponse.getBody());
    assertEquals(expected, actual);
  }
}
