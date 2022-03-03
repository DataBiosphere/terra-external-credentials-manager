package bio.terra.externalcreds.models;

import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface Secret extends WithSecret {
  Integer getId();

  String getUserId();

  String getName();

  Optional<String> getDescription();

  SecretType getType();

  Optional<String> getAttributes();

  String getSecretContent();

  class Builder extends ImmutableSecret.Builder {}
}
