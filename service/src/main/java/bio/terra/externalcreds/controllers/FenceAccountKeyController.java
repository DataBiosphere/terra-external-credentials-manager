package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.FenceAccountKeyApi;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.services.FenceAccountKeyService;
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
    FenceAccountKeyService fenceAccountKeyService,
    HttpServletRequest request,
    LinkedAccountService linkedAccountService)
    implements FenceAccountKeyApi {

  @Override
  public ResponseEntity<String> getFenceAccountKey(Provider provider) {
    var samUser = samUserFactory.from(request);
    var auditLogEvenBuilder = new AuditLogEvent.Builder()
        .auditLogEventType(AuditLogEventType.GetServiceAccountKey)
        .clientIP(request.getRemoteAddr());
    var maybeLinkedAccount =
        linkedAccountService.getLinkedAccount(samUser.getSubjectId(), provider);
    Optional<FenceAccountKey> maybeFenceAccountKey =
        maybeLinkedAccount.flatMap(
            linkedAccount -> {
              auditLogEvenBuilder
                  .provider(linkedAccount.getProvider())
                  .userId(linkedAccount.getUserId())
                  .externalUserId(linkedAccount.getExternalUserId());
              return fenceAccountKeyService.getFenceAccountKey(
                  linkedAccount.getUserId(), linkedAccount.getProvider());
            });
    var response = maybeFenceAccountKey.flatMap(
        fenceAccountKey -> {
          // service account key should not be expired but if it is (due to some failure
          // in ECM)
          // don't pass that failure on to the caller
          if (fenceAccountKey.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
          } else {
            auditLogger.logEvent(
                auditLogEvenBuilder.build());
            return Optional.of(fenceAccountKey.getKeyJson());
          }
        });
    return ResponseEntity.of(response);
  }
}
