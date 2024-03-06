package bio.terra.externalcreds.models;

import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
public interface DistributedLock extends WithDistributedLock {
  String getLockName();

  String getUserId();

  Instant getExpiresAt();

  class Builder extends ImmutableDistributedLock.Builder {}
}
