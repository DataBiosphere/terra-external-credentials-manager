package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(typeImmutable = "*", typeAbstract = "*Interface")
public interface GA4GHVisaInterface {
  Optional<Integer> getId();

  Optional<Integer> getPassportId();

  String getVisaType();

  Timestamp getExpires();

  String getJwt();

  String getIssuer();

  TokenTypeEnum getTokenType();

  Optional<Timestamp> getLastValidated();
}
