package bio.terra.externalcreds.auditLogging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AuditLogger as a separate component to centralize audit logging functions and ensure that all
 * audit events use the same logger.
 */
@Component
@Slf4j
public record AuditLogger(ObjectMapper mapper) {

  public void logEvent(AuditLogEvent event) {
    // mapper.valueToTree(event) converts event to a JsonNode which is handled specially by
    // bio.terra.common.logging.GoogleJsonLayout
    log.info(event.getAuditLogEventType().toString(), mapper.valueToTree(event));
  }
}
