package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface NihAccount extends WithNihAccount {
  Optional<Integer> getId();

  String getUserId();

  String getNihUsername();

  Timestamp getExpires();

  default boolean isExpired() {
    return getExpires().toInstant().isBefore(Instant.now());
  }

  class Builder extends ImmutableNihAccount.Builder {}
}
