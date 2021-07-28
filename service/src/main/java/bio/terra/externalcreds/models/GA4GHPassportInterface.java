package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(typeImmutable = "*", typeAbstract = "*Interface")
public interface GA4GHPassportInterface {
  Optional<Integer> getId();

  Optional<Integer> getLinkedAccountId();

  String getJwt();

  Timestamp getExpires();
}
