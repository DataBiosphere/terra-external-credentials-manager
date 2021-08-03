package scripts.testscripts;

import bio.terra.externalcreds.api.PublicApi;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import scripts.utils.ClientTestUtils;

@Slf4j
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

         log.info("Checking service status endpoint now.");
         var apiClient = ClientTestUtils.getClientWithoutAccessToken(server);
         var publicApi = new PublicApi(apiClient);
         publicApi.getStatus();
         var httpCode = publicApi.getApiClient().getStatusCode();
         log.info("Service status return code: {}", httpCode);
    }
}

