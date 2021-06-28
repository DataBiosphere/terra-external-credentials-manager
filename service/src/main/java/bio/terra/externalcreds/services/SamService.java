package bio.terra.externalcreds.services;

import bio.terra.common.iam.BearerTokenParser;
import bio.terra.externalcreds.ExternalCredsException;
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SamService {

  private final HttpServletRequest request;
  private final String samBasePath = "https://sam.dsde-dev.broadinstitute.org";
  private final SamService samService;

  public SamService(HttpServletRequest request, SamService samService) {
    this.request = request;
    this.samService = samService;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return client.setBasePath(samBasePath);
  }

  public UsersApi samUsersApi(String accessToken) {
    return new UsersApi(getApiClient(accessToken));
  }

  public String getUserIdFromSam() {
    try {
      String accessToken = BearerTokenParser.parse(this.request.getHeader("authorization"));
      return samService.samUsersApi(accessToken).getUserStatusInfo().getUserSubjectId();
    } catch (ApiException e) {
      throw new ExternalCredsException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
