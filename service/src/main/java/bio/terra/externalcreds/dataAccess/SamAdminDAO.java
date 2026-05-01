package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.AdminApi;
import org.springframework.stereotype.Component;

@Component
public class SamAdminDAO {

  private final ExternalCredsConfig config;

  public SamAdminDAO(ExternalCredsConfig config) {
    this.config = config;
  }

  public boolean resourceTypeAdminPermission(String accessToken, String resourceType, String action)
      throws ApiException {
    var apiClient = new ApiClient();
    apiClient.setBasePath(config.getSamBasePath());
    apiClient.setAccessToken(accessToken);
    return Boolean.TRUE.equals(
        new AdminApi(apiClient).resourceTypeAdminPermission(resourceType, action));
  }
}
