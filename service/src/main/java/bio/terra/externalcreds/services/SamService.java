package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.springframework.stereotype.Service;

@Service
public class SamService {
  private final ExternalCredsConfig externalCredsConfig;

  public SamService(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  public UsersApi samUsersApi(String accessToken) {
    var client = new ApiClient();
    client.setAccessToken(accessToken);
    return new UsersApi(client.setBasePath(this.externalCredsConfig.getSamBasePath()));
  }
}
