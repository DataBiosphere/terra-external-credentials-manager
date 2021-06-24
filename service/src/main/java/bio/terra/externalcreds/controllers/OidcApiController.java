package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerTokenParser;
import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.services.AccountLinkService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.SamService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class OidcApiController implements OidcApi {

  private final ProviderService providerService;
  private final AccountLinkService accountLinkService;
  private final SamService samService;
  private final HttpServletRequest request;

  public OidcApiController(
      ProviderService providerService,
      AccountLinkService accountLinkService,
      SamService samService,
      HttpServletRequest request) {
    this.providerService = providerService;
    this.accountLinkService = accountLinkService;
    this.samService = samService;
    this.request = request;
  }

  @Override
  @GetMapping("/api/oidc/v1/providers")
  public ResponseEntity<List<String>> listProviders() {
    List<String> providers = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providers);

    return new ResponseEntity<>(providers, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<LinkInfo> getLink(String provider) {

    // TODO: enforce that (user_id, provider_id) is unique in the DB!
    // TODO: Consider renaming "AccountLinkService" to "LinkedAccountService" or other

    try {
      // TODO: pull this out into its own function for reusibility
      // testing
      //      String request = getRequest().map(r -> r.getHeader("authorization")).orElse("nothing
      // found");
      //      System.out.println(getRequest().toString() + "_______");
      //      String accessToken =
      //          getRequest()
      //              .map(r -> BearerTokenParser.parse(r.getHeader("authorization")))
      //              .orElseThrow(() -> new UnauthorizedException("Access token header not
      // found."));
      System.out.println(this.request.getHeaderNames() + "_____________");
      String accessToken = BearerTokenParser.parse(this.request.getHeader("authorization"));
      String userId = samService.samUsersApi(accessToken).getUserStatusInfo().getUserSubjectId();
      System.out.println(userId + "____________");

      LinkInfo link = accountLinkService.getAccountLink(userId, provider);

      return new ResponseEntity<>(link, HttpStatus.OK);

      // TODO handle exceptions without this big try catch block where possible
    } catch (EmptyResultDataAccessException e) {
      // TODO look into why/where the EmptyResultDataAccessException is actually being thrown
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (SQLException e) {
      // catch this in the DAO
      log.warn("Encountered a SQL Exception while getting linked account information:", e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    } catch (ApiException e) {
      log.warn("Encountered an exception while getting the user's access token:", e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    } catch (UnauthorizedException e) {
      return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }
  }
}
