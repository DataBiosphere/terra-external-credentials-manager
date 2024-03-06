package bio.terra.externalcreds.models;

import java.time.Instant;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface FenceAccountKey extends WithFenceAccountKey {
  Optional<Integer> getId();

  Integer getLinkedAccountId();

  String getKeyJson();

  Instant getExpiresAt();

  class Builder extends ImmutableFenceAccountKey.Builder {}
}
