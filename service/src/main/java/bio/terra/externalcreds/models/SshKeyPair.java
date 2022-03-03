package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.SshKeyPairType;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface SshKeyPair extends WithSshKeyPair {
  Optional<Integer> getId();

  String getUserId();

  SshKeyPairType getType();

  String getExternalUserEmail();

  String getPrivateKey();

  String getPublicKey();

  class Builder extends ImmutableSshKeyPair.Builder {}
}
