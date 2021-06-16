package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.services.ProviderService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OidcApiController implements OidcApi {

  private final ProviderService providerService;

  public OidcApiController(ProviderService providerService) {
    this.providerService = providerService;
  }

  @Override
  @GetMapping("/api/oidc/v1/providers")
  public ResponseEntity<List<String>> listProviders() {
    List<String> providers = providerService.getProviderList();

    return new ResponseEntity<>(providers, HttpStatus.OK);
  }

  @Override
  @GetMapping("/api/oidc/v1/{provider}")
  public ResponseEntity<LinkInfo> getLink(String provider) {

    // QUESTIONS:
    // - does each provider only have one link?
    // - how do we get the id of the authenticated user?
    // - are we enforcing that (user_id, provider_id) is unique?


    LinkInfo link = new LinkInfo();

    return new ResponseEntity<>(link, HttpStatus.OK);

    // return a 404 if none exists
  }
}
