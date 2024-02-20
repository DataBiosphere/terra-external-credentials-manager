package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OauthApi;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record OauthApiController(
    HttpServletRequest request,
    ObjectMapper mapper,
    ProviderService providerService,
    ExternalCredsSamUserFactory samUserFactory)
    implements OauthApi {

  @Override
  public ResponseEntity<String> getAuthorizationUrl(Provider providerName, String redirectUri) {
    var samUser = samUserFactory.from(request);

    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            samUser.getSubjectId(), providerName.toString(), redirectUri);

    return ResponseEntity.of(authorizationUrl);
  }
}
