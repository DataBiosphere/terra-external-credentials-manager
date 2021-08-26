package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface GA4GHVisa extends WithGA4GHVisa {
  Optional<Integer> getId();

  Optional<Integer> getPassportId();

  String getVisaType();

  Timestamp getExpires();

  String getJwt();

  String getIssuer();

  TokenTypeEnum getTokenType();

  Optional<Timestamp> getLastValidated();

  class Builder extends ImmutableGA4GHVisa.Builder {}
}
