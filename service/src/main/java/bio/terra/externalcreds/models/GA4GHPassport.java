package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface GA4GHPassport extends WithGA4GHPassport {
  Optional<Integer> getId();

  Optional<Integer> getLinkedAccountId();

  String getJwt();

  Timestamp getExpires();

  /**
   * This comes from the jti claim of a jwt
   * https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.7
   */
  String getJwtId();

  class Builder extends ImmutableGA4GHPassport.Builder {}
}
