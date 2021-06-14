package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OidcApi;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OidcApiController implements OidcApi {

  @Override
  @GetMapping("/api/oidc/v1/providers")
  public ResponseEntity<List<String>> listProviders() {
    // TODO: actually read the config
    List<String> providers = new ArrayList<>();
    providers.add("hello");

    return new ResponseEntity(providers, HttpStatus.OK);
  }
}
