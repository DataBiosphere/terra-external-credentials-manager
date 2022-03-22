package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerTokenParser;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.services.SamService;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.http.HttpStatus;

/** Utils class to get user status info from SAM. */
public class UserStatusInfoUtils {

  public static String getUserIdFromSam(HttpServletRequest request, SamService samService) {
    try {
      var header =
          Optional.ofNullable(request.getHeader("authorization"))
              .orElseThrow(() -> new UnauthorizedException("User is not authorized"));
      var accessToken = BearerTokenParser.parse(header);

      return samService.samUsersApi(accessToken).getUserStatusInfo().getUserSubjectId();
    } catch (ApiException e) {
      throw new ExternalCredsException(
          e,
          e.getCode() == HttpStatus.NOT_FOUND.value()
              ? HttpStatus.FORBIDDEN
              : HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
