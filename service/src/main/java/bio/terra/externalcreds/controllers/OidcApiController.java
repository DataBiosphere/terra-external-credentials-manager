package bio.terra.externalcreds.controllers;

import bio.terra.common.iam.BearerTokenParser;
import bio.terra.externalcreds.ExternalCredsException;
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
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OidcApiController implements OidcApi {

  private final ProviderService providerService;
  private final LinkedAccountService linkedAccountService;
  private final SamService samService;
  private final HttpServletRequest request;

  public OidcApiController(
      ProviderService providerService,
      LinkedAccountService accountLinkService,
      SamService samService,
      HttpServletRequest request) {
    this.providerService = providerService;
    this.linkedAccountService = accountLinkService;
    this.samService = samService;
    this.request = request;
  }

  private String getUserIdFromSam() {
    try {
      String accessToken = BearerTokenParser.parse(this.request.getHeader("authorization"));
      return samService.samUsersApi(accessToken).getUserStatusInfo().getUserSubjectId();
    } catch (ApiException e) {
      throw new ExternalCredsException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  @GetMapping("/api/oidc/v1/providers")
  public ResponseEntity<List<String>> listProviders() {
    List<String> providers = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providers);

    return new ResponseEntity<>(providers, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<LinkInfo> getLink(String provider) {

    String userId = getUserIdFromSam();
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
}
