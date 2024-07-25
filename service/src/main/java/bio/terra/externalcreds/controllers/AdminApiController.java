package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.api.AdminApi;
import bio.terra.externalcreds.generated.model.AdminLinkInfo;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record AdminApiController(
    HttpServletRequest request,
    ObjectMapper mapper,
    LinkedAccountService linkedAccountService,
    ExternalCredsSamUserFactory samUserFactory,
    ExternalCredsConfig externalCredsConfig)
    implements AdminApi {

  @Override
  public ResponseEntity<Void> putLinkedAccountWithFakeToken(
      Provider provider, AdminLinkInfo adminLinkInfo) {
    requireAdmin();
    var linkedAccount =
        new LinkedAccount.Builder()
            .isAuthenticated(true)
            .provider(provider)
            .userId(adminLinkInfo.getUserId())
            .refreshToken("fake-refresh-token")
            .externalUserId(adminLinkInfo.getLinkedExternalId())
            .expires(Timestamp.from(adminLinkInfo.getLinkExpireTime().toInstant()))
            .build();
    linkedAccountService.upsertLinkedAccount(linkedAccount);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<List<AdminLinkInfo>> getActiveLinkedAccounts(Provider provider) {
    requireAdmin();
    var activeLinkedAccounts = linkedAccountService.getActiveLinkedAccounts(provider);
    return ResponseEntity.ok(
        activeLinkedAccounts.stream().map(OpenApiConverters.Output::convertAdmin).toList());
  }

  @Override
  public ResponseEntity<AdminLinkInfo> getLinkedAccountForExternalId(
      Provider provider, String externalId) {
    requireAdmin();
    var linkedAccount = linkedAccountService.getLinkedAccountForExternalId(provider, externalId);
    return ResponseEntity.of(linkedAccount.map(OpenApiConverters.Output::convertAdmin));
  }

  private void requireAdmin() {
    var samUser = samUserFactory.from(request);
    if (!externalCredsConfig.getAuthorizedAdmins().contains(samUser.getEmail())) {
      throw new ForbiddenException("Admin permissions required");
    }
  }
}
