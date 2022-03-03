package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.SshKeyType;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface SshKeyPair extends WithSshKeyPair {
  Optional<Integer> getId();

  String getUserId();

  SshKeyType getType();

  String getExternalUserEmail();

  String getPrivateKey();

  String getPublicKey();

  class Builder extends ImmutableSshKeyPair.Builder {}
}
