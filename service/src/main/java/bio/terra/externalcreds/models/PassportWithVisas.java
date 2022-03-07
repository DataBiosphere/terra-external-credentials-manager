package bio.terra.externalcreds.models;

import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public interface PassportWithVisas extends WithPassportWithVisas {
  GA4GHPassport getPassport();

  List<GA4GHVisa> getVisas();

  class Builder extends ImmutablePassportWithVisas.Builder {}
}
