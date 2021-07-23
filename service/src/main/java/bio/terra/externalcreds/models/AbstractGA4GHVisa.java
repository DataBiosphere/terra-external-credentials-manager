package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(typeImmutable = "*")
public interface AbstractGA4GHVisa {
  Optional<Integer> getId();

  Optional<Integer> getPassportId();

  String getVisaType();

  Timestamp getExpires();

  String getJwt();

  String getIssuer();

  TokenTypeEnum getTokenType();

  Optional<Timestamp> getLastValidated();
}
