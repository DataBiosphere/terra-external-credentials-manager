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
    return sshKeyPairDAO.getSecret(userId, type);
  }

  @WriteTransaction
  public SshKeyPair upsertSshKeyPair(
      bio.terra.externalcreds.generated.model.SshKeyPair sshKeyPair) {
    return null;
//        return sshKeyPairDAO.upsertSshKey(
//          new SshKeyPair.Builder()
//              .privateKey(sshKeyPair.getPrivateKey())
//              .publicKey(sshKeyPair.getPublicKey())
//              .
//        );
  }
}
