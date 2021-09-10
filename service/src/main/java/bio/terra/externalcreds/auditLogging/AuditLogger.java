package bio.terra.externalcreds.auditLogging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditLogger {
  private final ObjectMapper mapper;

  public AuditLogger(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void logEvent(AuditLogEvent event) {
    log.info(event.getAuditLogEventType().toString(), mapper.valueToTree(event));
  }
}
