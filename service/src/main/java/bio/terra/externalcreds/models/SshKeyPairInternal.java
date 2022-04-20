package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.SshKeyPairType;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface SshKeyPairInternal extends WithSshKeyPairInternal {
  Optional<Integer> getId();

  String getUserId();

  SshKeyPairType getType();

  String getExternalUserEmail();

  byte[] getPrivateKey();

  String getPublicKey();

  class Builder extends ImmutableSshKeyPairInternal.Builder {}
}
