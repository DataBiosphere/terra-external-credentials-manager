package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SshKeyPairService {

  private final SshKeyPairDAO sshKeyPairDAO;

  public SshKeyPairService(SshKeyPairDAO sshKeyPairDAO) {
    this.sshKeyPairDAO = sshKeyPairDAO;
  }

  @ReadTransaction
  public Optional<SshKeyPairInternal> getSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO.getSshKeyPair(userId, type);
  }

  @WriteTransaction
  public SshKeyPairInternal putSshKeyPair(
      String userId, SshKeyPairType type, SshKeyPair sshKeyPair) {
    return sshKeyPairDAO.upsertSshKeyPair(
        new SshKeyPairInternal.Builder()
            .privateKey(sshKeyPair.getPrivateKey())
            .publicKey(sshKeyPair.getPublicKey())
            .externalUserEmail(sshKeyPair.getExternalUserEmail())
            .userId(userId)
            .type(type)
            .build());
  }

  @WriteTransaction
  public boolean deleteSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO.deleteSshKeyPair(userId, type);
  }
}
