package bio.terra.externalcreds.models;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface LinkedAccountWithPassportAndVisas {
  LinkedAccount getLinkedAccount();

  Optional<GA4GHPassport> getPassport();

  @Value.Default
  default List<GA4GHVisa> getVisas() {
    return Collections.emptyList();
  }
}
