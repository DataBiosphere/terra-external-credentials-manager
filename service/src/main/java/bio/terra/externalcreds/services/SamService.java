package bio.terra.externalcreds.services;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerTokenParser;
import bio.terra.externalcreds.ExternalCredsException;
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SamService {

  private final HttpServletRequest request;

  @Value("${externalcreds.sam-address}")
  private String samBasePath;

  public SamService(HttpServletRequest request) {
    this.request = request;
  }

  public UsersApi samUsersApi(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return new UsersApi(client.setBasePath(samBasePath));
  }

  public String getUserIdFromSam() {
    try {
      String header = request.getHeader("authorization");
      if (header == null) throw new UnauthorizedException("User is not authorized");
      String accessToken = BearerTokenParser.parse(header);

      return samUsersApi(accessToken).getUserStatusInfo().getUserSubjectId();
    } catch (ApiException e) {
      throw new ExternalCredsException(e, HttpStatus.FORBIDDEN);
    }
  }
}
