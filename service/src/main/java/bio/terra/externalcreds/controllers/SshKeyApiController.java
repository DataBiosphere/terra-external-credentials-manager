package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.SshKeyPairApi;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.services.SshKeyPairService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record SshKeyApiController(
    HttpServletRequest request,
    SshKeyPairService sshKeyPairService,
    AuditLogger auditLogger,
    ObjectMapper objectMapper,
    ExternalCredsSamUserFactory samUserFactory)
    implements SshKeyPairApi {

  @Override
  public ResponseEntity<Void> deleteSshKeyPair(SshKeyPairType type) {
    var samUser = samUserFactory.from(request);

    var auditLoggerBuilder =
        new AuditLogEvent.Builder()
            .sshKeyPairType(type.name())
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr());
    try {
      sshKeyPairService.deleteSshKeyPair(samUser.getSubjectId(), type);
      auditLogger.logEvent(
          auditLoggerBuilder.auditLogEventType(AuditLogEventType.SshKeyPairDeleted).build());
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLoggerBuilder.auditLogEventType(AuditLogEventType.SshKeyPairDeletionFailed).build());
      throw e;
    }
  }

  @Override
  public ResponseEntity<SshKeyPair> getSshKeyPair(SshKeyPairType type, Boolean includePrivateKey) {
    var samUser = samUserFactory.from(request);
    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .sshKeyPairType(type.name())
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr());
    try {
      var sshKeyPair = sshKeyPairService.getSshKeyPair(samUser.getSubjectId(), type);
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.GetSshKeyPairSucceeded).build());
      return ResponseEntity.ok(OpenApiConverters.Output.convert(sshKeyPair, includePrivateKey));
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.GetSshKeyPairFailed).build());
      throw e;
    }
  }

  @Override
  public ResponseEntity<SshKeyPair> putSshKeyPair(SshKeyPairType type, SshKeyPair body) {
    var samUser = samUserFactory.from(request);
    var sshKeyPair = sshKeyPairService.putSshKeyPair(samUser.getSubjectId(), type, body);
    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .sshKeyPairType(type.name())
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr())
            .auditLogEventType(AuditLogEventType.PutSshKeyPair)
            .build());
    return ResponseEntity.ok(
        OpenApiConverters.Output.convert(sshKeyPair, /*includePrivateKey=*/ false));
  }

  @Override
  public ResponseEntity<SshKeyPair> generateSshKeyPair(
      SshKeyPairType type, String email, Boolean includePrivateKey) {
    String userEmail;
    try {
      userEmail = objectMapper.readValue(email, String.class);
    } catch (JsonProcessingException e) {
      throw new BadRequestException("Fail to parse json string email");
    }
    var samUser = samUserFactory.from(request);
    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr())
            .sshKeyPairType(type.name());
    try {
      var generatedKey =
          sshKeyPairService.generateSshKeyPair(samUser.getSubjectId(), userEmail, type);
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.SshKeyPairCreated).build());
      return ResponseEntity.ok(OpenApiConverters.Output.convert(generatedKey, includePrivateKey));
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder
              .auditLogEventType(AuditLogEventType.SshKeyPairCreationFailed)
              .build());
      throw e;
    }
  }
}
