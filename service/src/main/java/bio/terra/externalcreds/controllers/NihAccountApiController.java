package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.api.NihAccountApi;
import bio.terra.externalcreds.generated.model.NihAccountModel;
import bio.terra.externalcreds.services.NihAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record NihAccountApiController(
    HttpServletRequest request,
    ObjectMapper mapper,
    NihAccountService nihAccountService,
    ExternalCredsSamUserFactory samUserFactory,
    ExternalCredsConfig externalCredsConfig)
    implements NihAccountApi {

  @Override
  public ResponseEntity<NihAccountModel> getNihAccount() {
    var samUser = samUserFactory.from(request);
    var nihAccount = nihAccountService.getNihAccountForUser(samUser.getSubjectId());
    return ResponseEntity.of(nihAccount.map(OpenApiConverters.Output::convert));
  }

  @Override
  public ResponseEntity<Void> linkNihAccount(NihAccountModel nihAccountModel) {
    requireAdmin();
    nihAccountService.upsertNihAccount(OpenApiConverters.Input.convert(nihAccountModel));
    return ResponseEntity.accepted().build();
  }

  @Override
  public ResponseEntity<NihAccountModel> getNihAccountForUsername(String nihUsername) {
    requireAdmin();
    var nihAccount = nihAccountService.getLinkedAccountForUsername(nihUsername);
    return ResponseEntity.of(nihAccount.map(OpenApiConverters.Output::convert));
  }

  @Override
  public ResponseEntity<List<NihAccountModel>> getActiveNihAccounts() {
    requireAdmin();
    var activeNihAccounts = nihAccountService.getActiveNihAccounts();
    return ResponseEntity.ok(
        activeNihAccounts.stream().map(OpenApiConverters.Output::convert).toList());
  }

  private void requireAdmin() {
    var samUser = samUserFactory.from(request);
    if (!externalCredsConfig.getAuthorizedAdmins().contains(samUser.getEmail())) {
      throw new ForbiddenException("Admin permissions required");
    }
  }
}
