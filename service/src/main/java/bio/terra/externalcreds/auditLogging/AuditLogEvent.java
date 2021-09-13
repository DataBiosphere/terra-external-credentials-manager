package bio.terra.externalcreds.auditLogging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableAuditLogEvent.class)
public interface AuditLogEvent extends WithAuditLogEvent {
  String getUserId();

  @JsonInclude(Include.NON_EMPTY)
  Optional<String> getClientIP();

  String getProvider();

  AuditLogEventType getAuditLogEventType();

  class Builder extends ImmutableAuditLogEvent.Builder {}
}
