package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.services.ProviderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class OidcApiController implements OidcApi {

  private final ProviderService providerService;
  private final ObjectMapper mapper;

  public OidcApiController(ProviderService providerService, ObjectMapper mapper) {
    this.providerService = providerService;
    this.mapper = mapper;
  }

  @Override
  public ResponseEntity<List<String>> listProviders() {
    List<String> providers = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providers);

    return ResponseEntity.ok(providers);
  }

  // Because we're just processing String -> json string, there shouldn't be any conversion issue.
  @SneakyThrows(JsonProcessingException.class)
  @Override
  public ResponseEntity<String> getAuthUrl(
      String provider, List<String> scopes, String redirectUri, String state) {
    String authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            provider, redirectUri, Set.copyOf(scopes), state);

    if (authorizationUrl == null) {
      return ResponseEntity.notFound().build();
    } else {
      // We explicitly run this through the mapper because otherwise it's treated as text/plain, and
      // not correctly quoted to be valid json.
      return ResponseEntity.ok(mapper.writeValueAsString(authorizationUrl));
    }
  }
}
