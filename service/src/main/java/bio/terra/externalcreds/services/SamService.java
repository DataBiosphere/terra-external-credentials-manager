package bio.terra.externalcreds.services;

import lombok.val;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SamService {

  @Value("${externalcreds.sam-address}")
  private String samBasePath;

  public UsersApi samUsersApi(String accessToken) {
    val client = new ApiClient();
    client.setAccessToken(accessToken);
    return new UsersApi(client.setBasePath(samBasePath));
  }
}
