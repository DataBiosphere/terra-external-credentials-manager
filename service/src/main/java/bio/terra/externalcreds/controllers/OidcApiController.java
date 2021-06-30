package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.SamService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
  private final LinkedAccountService linkedAccountService;
  private final SamService samService;

  public OidcApiController(
      ProviderService providerService,
      LinkedAccountService linkedAccountService,
      SamService samService) {
    this.providerService = providerService;
    this.linkedAccountService = linkedAccountService;
    this.samService = samService;
  }

  @Override
  public ResponseEntity<List<String>> listProviders() {
    List<String> providers = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providers);

    return new ResponseEntity<>(providers, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<LinkInfo> getLink(String provider) {
    String userId = samService.getUserIdFromSam();
    LinkedAccount linkedAccount = linkedAccountService.getLinkedAccount(userId, provider);
    if (linkedAccount == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    OffsetDateTime expTime =
        OffsetDateTime.ofInstant(linkedAccount.getExpires().toInstant(), ZoneId.of("UTC"));
    LinkInfo linkInfo =
        new LinkInfo()
            .externalUserId(linkedAccount.getExternalUserId())
            .expirationTimestamp(expTime);

    return new ResponseEntity<>(linkInfo, HttpStatus.OK);
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
