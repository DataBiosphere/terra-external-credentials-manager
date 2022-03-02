package bio.terra.externalcreds.models;

import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface SshKey extends WithSshKey {
  Integer getId();

  String getUserId();

  String getName();

  Optional<String> getDescription();

  SecretType getType();

  Optional<String> getExternalUserName();

  Optional<String> getExternalUserEmail();

  String getSecretContent();

  class Builder extends ImmutableSshKey.Builder {}
}
