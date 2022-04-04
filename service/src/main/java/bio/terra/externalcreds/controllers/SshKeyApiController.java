package bio.terra.externalcreds.controllers;

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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record SshKeyApiController(
    HttpServletRequest request,
    SamService samService,
    SshKeyPairService sshKeyPairService,
    AuditLogger auditLogger)
    implements SshKeyPairApi {

  @Override
  public ResponseEntity<Void> deleteSshKeyPair(SshKeyPairType type) {
    var userId = getUserIdFromSam(request, samService);

    var auditLoggerBuilder =
        new AuditLogEvent.Builder()
            .sshKeyPairType(type.name())
            .userId(userId)
            .clientIP(request.getRemoteAddr());
    try {
      sshKeyPairService.deleteSshKeyPair(userId, type);
      auditLogger.logEvent(
          auditLoggerBuilder.auditLogEventType(AuditLogEventType.SshKayPairDeleted).build());
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLoggerBuilder.auditLogEventType(AuditLogEventType.SshKeyPairDeletionFailed).build());
      throw e;
    }
  }

  @Override
  public ResponseEntity<SshKeyPair> getSshKeyPair(SshKeyPairType type) {
    var userId = getUserIdFromSam(request, samService);
    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .sshKeyPairType(type.name())
            .userId(userId)
            .clientIP(request.getRemoteAddr());
    try {
      var sshKeyPair = sshKeyPairService.getSshKeyPair(userId, type);
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.GetSshKeyPairSucceeded).build());
      return ResponseEntity.ok(OpenApiConverters.Output.convert(sshKeyPair));
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.GetSshKeyPairFailed).build());
      throw e;
    }
  }

  @Override
  public ResponseEntity<SshKeyPair> putSshKeyPair(SshKeyPairType type, SshKeyPair body) {
    var sshKeyPair =
        sshKeyPairService.putSshKeyPair(getUserIdFromSam(request, samService), type, body);
    return ResponseEntity.ok(OpenApiConverters.Output.convert(sshKeyPair));
  }

  @Override
  public ResponseEntity<SshKeyPair> generateSshKeyPair(SshKeyPairType type, String email) {
    var userId = getUserIdFromSam(request, samService);
    var auditLogEventBuilder =
        new AuditLogEvent.Builder().userId(userId).clientIP(request.getRemoteAddr());
    try {
      var generatedKey = sshKeyPairService.generateSshKeyPair(userId, email, type);
      auditLogger.logEvent(
          auditLogEventBuilder
              .auditLogEventType(AuditLogEventType.SshKeyPairCreated)
              .sshKeyPairType(generatedKey.getType().name())
              .build());
      return ResponseEntity.ok(OpenApiConverters.Output.convert(generatedKey));
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder
              .auditLogEventType(AuditLogEventType.SshKeyPairCreationFailed)
              .build());
      throw e;
    }
  }
}
