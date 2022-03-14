package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPair;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SshKeyPairService {

  private final SshKeyPairDAO sshKeyPairDAO;

  public SshKeyPairService(SshKeyPairDAO sshKeyPairDAO) {
    this.sshKeyPairDAO = sshKeyPairDAO;
  }

  @ReadTransaction
  public Optional<SshKeyPair> getSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO.getSshKeyPair(userId, type);
  }

  @WriteTransaction
  public SshKeyPair putSshKeyPair(
      String userId,
      SshKeyPairType type,
      String privateKey,
      String publicKey,
      String externalUserEmail) {
    return sshKeyPairDAO.upsertSshKeyPair(
        new SshKeyPair.Builder()
            .privateKey(privateKey)
            .publicKey(publicKey)
            .externalUserEmail(externalUserEmail)
            .userId(userId)
            .type(type)
            .build());
  }

  @WriteTransaction
  public boolean deleteSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO.deleteSshKeyPair(userId, type);
  }
}
