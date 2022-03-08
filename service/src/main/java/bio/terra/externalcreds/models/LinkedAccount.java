package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.LinkInfo;
import java.sql.Timestamp;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface LinkedAccount extends WithLinkedAccount {
  Optional<Integer> getId();

  String getUserId();

  String getProviderName();

  String getRefreshToken();

  Timestamp getExpires();

  String getExternalUserId();

  boolean isAuthenticated();

  class Builder extends ImmutableLinkedAccount.Builder {}

  default LinkInfo toLinkInfo() {
    return new LinkInfo()
        .externalUserId(getExternalUserId())
        .expirationTimestamp(getExpires())
        .authenticated(isAuthenticated());
  }
}
