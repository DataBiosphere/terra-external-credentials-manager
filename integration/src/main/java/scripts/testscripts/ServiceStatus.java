package scripts.testscripts;

import bio.terra.externalcreds.client.ApiClient;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
// import bio.terra.workspace.api.UnauthenticatedApi;
// import bio.terra.workspace.client.ApiClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
// import lombok.extern.slf4j.Slf4j;
import scripts.utils.ClientTestUtils;

// @Slf4j
public class ServiceStatus extends TestScript {
    private Duration delay = Duration.ZERO;

    @Override
    public void setParameters(List<String> parameters) {

        if (parameters == null || parameters.size() == 0) {
            return;
        }
        delay = Duration.ofSeconds(Long.parseLong(parameters.get(0)));
    }

    @Override
    public void userJourney(TestUserSpecification testUser) throws Exception {
        if (delay.getSeconds() > 0) TimeUnit.SECONDS.sleep(delay.getSeconds());

        // TODO: use the ECM client here
        // log.info("Checking service status endpoint now.");
        // var apiClient = ClientTestUtils.getClientWithoutAccessToken(server);
        // var unauthenticatedApi = new UnauthenticatedApi(apiClient);
        // unauthenticatedApi.serviceStatus();
        // var httpCode = unauthenticatedApi.getApiClient().getStatusCode();
        // log.info("Service status return code: {}", httpCode);
    }
}

