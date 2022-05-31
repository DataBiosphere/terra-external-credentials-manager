package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.springframework.stereotype.Service;

@Service
public class SamService {
  private final ExternalCredsConfig externalCredsConfig;

  public SamService(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  public ApiClient samApiClient() {
    return new ApiClient().setBasePath(this.externalCredsConfig.getSamBasePath());
  }
}
