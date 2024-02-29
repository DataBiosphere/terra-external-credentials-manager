package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import org.immutables.value.Value;

@Value.Immutable
public interface DistributedLock extends WithDistributedLock {
  String getLockName();

  String getUserId();

  Timestamp getExpiresAt();

  class Builder extends ImmutableDistributedLock.Builder {}
}
