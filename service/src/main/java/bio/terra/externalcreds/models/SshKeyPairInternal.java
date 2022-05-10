package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.SshKeyPairType;
import java.time.Instant;
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

  Optional<Instant> getLastEncryptedTimestamp();

  class Builder extends ImmutableSshKeyPairInternal.Builder {}
}
