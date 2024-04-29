package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SamStatusDAO {
  private final StatusApi samStatusApi;

  public SamStatusDAO(ExternalCredsConfig externalCredsConfig) {
    this.samStatusApi = createSamStatusApi(externalCredsConfig);
  }

  public SystemStatus getSamStatus() throws ApiException {
    return samStatusApi.getSystemStatus();
  }

  private StatusApi createSamStatusApi(ExternalCredsConfig externalCredsConfig) {
    var apiClientBuilder = new ApiClient().getHttpClient().newBuilder();
    var httpClient = apiClientBuilder.build();
    ApiClient samApiClient = new ApiClient();
    samApiClient.setHttpClient(httpClient);
    samApiClient.setBasePath(externalCredsConfig.getSamBasePath());
    return new StatusApi(samApiClient);
  }
}
