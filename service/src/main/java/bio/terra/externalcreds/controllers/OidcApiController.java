package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.generated.api.OidcApi;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OidcApiController implements OidcApi {

  private final ProviderConfig providerConfig;

  public OidcApiController(ProviderConfig providerConfig) {
    this.providerConfig = providerConfig;
  }

  @Override
  @GetMapping("/api/oidc/v1/providers")
  public ResponseEntity<List<String>> listProviders() {
    List<String> providers = List.copyOf(providerConfig.getServices().keySet());

    return new ResponseEntity<>(providers, HttpStatus.OK);
  }
}
