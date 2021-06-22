package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.services.ProviderService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
  @GetMapping("/api/oidc/v1/{provider}")
  public ResponseEntity<LinkInfo> getLink(String provider) {

    // QUESTIONS:
    // - how do we get the id of the authenticated user?
    // - does each user-provider combo only have one link?
    // - are we enforcing that (user_id, provider_id) is unique?

    LinkInfo link = accountLinkService.getAccountLink("", provider);

    return new ResponseEntity<>(link, HttpStatus.OK);

    // return a 404 if none exists
  }
}
