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

  public OidcApiController(ProviderService providerService) {
    this.providerService = providerService;
  }

  @Override
  @GetMapping("/api/oidc/v1/providers")
  public ResponseEntity<List<String>> listProviders() {
    List<String> providers = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providers);

    return new ResponseEntity<>(providers, HttpStatus.OK);
  }
}
