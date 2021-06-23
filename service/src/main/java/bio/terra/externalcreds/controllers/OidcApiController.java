package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.services.AccountLinkService;
import bio.terra.externalcreds.services.ProviderService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OidcApiController implements OidcApi {

  private final ProviderService providerService;
  private final AccountLinkService accountLinkService;

  public OidcApiController(ProviderService providerService, AccountLinkService accountLinkService) {
    this.providerService = providerService;
    this.accountLinkService = accountLinkService;
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

    // TODO Questions:
    // - how do we get the id of the authenticated user?
    // - are we enforcing that (user_id, provider_id) is unique?

    String userId = "fake_user_id"; // TODO: stop hardcoding this

    try {
      LinkInfo link = accountLinkService.getAccountLink(userId, provider);
      return new ResponseEntity<>(link, HttpStatus.OK);
    } catch (EmptyResultDataAccessException e) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } catch (SQLException e) {
      log.warn("Encountered a SQL Exception while getting linked account information:", e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
