package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.services.ProviderService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class OidcApiController implements OidcApi {

  private final ProviderService providerService;

  public OidcApiController(ProviderService providerService) {
    this.providerService = providerService;
  }

  @Override
  public ResponseEntity<List<String>> listProviders() {
    List<String> providers = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providers);

    return new ResponseEntity<>(providers, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<String> getAuthUrl(
      String provider, List<String> scopes, String redirectUri, String state) {
    String authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            provider, redirectUri, Set.copyOf(scopes), state);

    if (authorizationUrl == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } else {
      return new ResponseEntity<>(authorizationUrl, HttpStatus.OK);
    }
  }
}
