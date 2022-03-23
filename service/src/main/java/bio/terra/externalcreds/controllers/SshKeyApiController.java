package bio.terra.externalcreds.controllers;

import static bio.terra.externalcreds.controllers.OpenApiConverters.Output.convert;
import static bio.terra.externalcreds.controllers.UserStatusInfoUtils.getUserIdFromSam;

import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.SshKeyPairApi;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.services.SamService;
import bio.terra.externalcreds.services.SshKeyPairService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class SshKeyApiController implements SshKeyPairApi {

  private final HttpServletRequest request;
  private final SamService samService;
  private final SshKeyPairService sshKeyPairService;
  private final AuditLogger auditLogger;

  public SshKeyApiController(
      HttpServletRequest request, SamService samService, SshKeyPairService sshKeyPairService,
      AuditLogger auditLogger) {
    this.request = request;
    this.samService = samService;
    this.sshKeyPairService = sshKeyPairService;
    this.auditLogger = auditLogger;
  }

  @Override
  public ResponseEntity<Void> deleteSshKeyPair(SshKeyPairType type) {
    sshKeyPairService.deleteSshKeyPair(getUserIdFromSam(request, samService), type);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<SshKeyPair> getSshKeyPair(SshKeyPairType type) {
    var sshKeyPair = sshKeyPairService.getSshKeyPair(getUserIdFromSam(request, samService), type);
    return ResponseEntity.of(sshKeyPair.map(keyPair -> convert(keyPair)));
  }

  @Override
  public ResponseEntity<SshKeyPair> generateSshKeyPair(SshKeyPairType type, String email) {
    var userId = getUserIdFromSam(request, samService);
    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .userId(userId)
            .clientIP(request.getRemoteAddr());
    var generatedKey = sshKeyPairService.generateSshKeyPair(userId, email, type);
    auditLogger.logEvent(
        auditLogEventBuilder
            .auditLogEventType(
                generatedKey
                    .map(x -> AuditLogEventType.SshKeyPairCreated)
                    .orElse(AuditLogEventType.SshKeyPairCreationFailed))
            .build());
    return ResponseEntity.of(
        generatedKey.map(
            x -> OpenApiConverters.Output.convert(x)));
  }
}
