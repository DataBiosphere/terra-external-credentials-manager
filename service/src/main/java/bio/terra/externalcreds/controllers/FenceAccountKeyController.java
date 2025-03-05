package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.FenceAccountKeyApi;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.services.FenceProviderService;
import bio.terra.externalcreds.services.LinkedAccountService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record FenceAccountKeyController(
    AuditLogger auditLogger,
    ExternalCredsSamUserFactory samUserFactory,
    LinkedAccountService linkedAccountService,
    FenceProviderService fenceProviderService,
    HttpServletRequest request)
    implements FenceAccountKeyApi {

  @Override
  public ResponseEntity<String> getFenceAccountKey(Provider provider) {
    throw new NotFoundException("Fence service accounts no longer supported");
  }
}
