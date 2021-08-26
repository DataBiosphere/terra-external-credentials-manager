package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface LinkedAccount extends WithLinkedAccount {
  Optional<Integer> getId();

  String getUserId();

  String getProviderId();

  String getRefreshToken();

  Timestamp getExpires();

  String getExternalUserId();

  @Value.Default
  default boolean isAuthenticated() {
    return true;
  }

  class Builder extends ImmutableLinkedAccount.Builder {}
}
