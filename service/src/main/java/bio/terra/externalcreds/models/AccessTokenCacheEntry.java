package bio.terra.externalcreds.models;

import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
public interface AccessTokenCacheEntry extends WithAccessTokenCacheEntry {

  Integer getLinkedAccountId();

  String getAccessToken();

  Instant getIssuedAt();

  Instant getExpiresAt();

  class Builder extends ImmutableAccessTokenCacheEntry.Builder {}
}
