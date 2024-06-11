package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.Provider;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface LinkedAccount extends WithLinkedAccount {
  Optional<Integer> getId();

  String getUserId();

  Provider getProvider();

  String getRefreshToken();

  Timestamp getExpires();

  String getExternalUserId();

  boolean isAuthenticated();

  default boolean isExpired() {
    return getExpires().toInstant().isBefore(Instant.now());
  }

  class Builder extends ImmutableLinkedAccount.Builder {}
}
