package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface GA4GHPassport {
  Optional<Integer> getId();

  Optional<Integer> getLinkedAccountId();

  String getJwt();

  Timestamp getExpires();
}
