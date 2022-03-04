package bio.terra.externalcreds.models;

import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface PassportWithVisas extends WithPassportWithVisas {
  Optional<GA4GHPassport> getPassport();

  List<GA4GHVisa> getVisas();

  class Builder extends ImmutablePassportWithVisas.Builder {}
}
