package bio.terra.externalcreds.models;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(typeImmutable = "*", typeAbstract = "*Interface")
public interface LinkedAccountWithPassportAndVisasInterface {
  LinkedAccount getLinkedAccount();

  Optional<GA4GHPassport> getPassport();

  @Value.Default
  default List<GA4GHVisa> getVisas() {
    return Collections.emptyList();
  }
}
