package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.SshKeyType;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface SshKey extends WithSshKey {
  Integer getId();

  String getUserId();

  SshKeyType getType();

  Optional<String> getExternalUserEmail();

  String getPrivateKey();

  class Builder extends ImmutableSshKey.Builder {}
}
