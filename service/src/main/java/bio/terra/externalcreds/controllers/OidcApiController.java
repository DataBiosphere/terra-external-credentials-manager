package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.services.ProviderService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
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

  @Override
  public ResponseEntity<LinkInfo> createLink(
      String provider, List<String> scopes, String redirectUri, String state, String oauthcode) {
    OAuth2AccessTokenResponse tokenResponse =
        providerService.authorizationCodeExchange(
            provider, oauthcode, redirectUri, Set.copyOf(scopes), state);

    log.error("access token: " + tokenResponse.getAccessToken().getTokenValue());
    log.error("refresh token: " + tokenResponse.getRefreshToken().getTokenValue());

    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
  }
}
