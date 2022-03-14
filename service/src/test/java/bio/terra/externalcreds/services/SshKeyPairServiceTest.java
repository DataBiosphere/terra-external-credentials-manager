package bio.terra.externalcreds.services;

import static bio.terra.externalcreds.TestUtils.createRandomGithubSshKey;
import static bio.terra.externalcreds.TestUtils.getRSAEncodedKeyPair;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPair;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SshKeyPairServiceTest extends BaseTest {

  @Autowired SshKeyPairService sshKeyPairService;
  @Autowired SshKeyPairDAO sshKeyPairDAO;

  @Test
  void getSshKeyPair() throws NoSuchAlgorithmException, IOException {
    var sshKey = createRandomGithubSshKey();
    sshKeyPairDAO.upsertSshKeyPair(sshKey);

    var loadedSshKey = sshKeyPairService.getSshKeyPair(sshKey.getUserId(), SshKeyPairType.GITHUB);

    verifySshKeyPair(sshKey, loadedSshKey.get());
  }

  @Test
  void deleteSshKeyPair() throws NoSuchAlgorithmException, IOException {
    var sshKey = createRandomGithubSshKey();
    sshKeyPairDAO.upsertSshKeyPair(sshKey);

    var successfullyDeleted =
        sshKeyPairService.deleteSshKeyPair(sshKey.getUserId(), SshKeyPairType.GITHUB);

    assertTrue(successfullyDeleted);
  }

  @Test
  void putSshKey() throws NoSuchAlgorithmException, IOException {
    var userId = UUID.randomUUID().toString();
    var externalUser = "foo@gmail.com";
    var pair = getRSAEncodedKeyPair(externalUser);
    var sshKeyPair =
        new SshKeyPair.Builder()
            .userId(userId)
            .type(SshKeyPairType.GITHUB)
            .externalUserEmail(externalUser)
            .privateKey(pair.getLeft())
            .publicKey(pair.getRight())
            .build();

    var storedSshKey =
        sshKeyPairService.putSshKeyPair(
            sshKeyPair.getUserId(),
            sshKeyPair.getType(),
            sshKeyPair.getPrivateKey(),
            sshKeyPair.getPublicKey(),
            sshKeyPair.getExternalUserEmail());

    verifySshKeyPair(sshKeyPair, storedSshKey);
  }

  @Test
  void updateSshKey() throws NoSuchAlgorithmException, IOException {
    var sshKey = createRandomGithubSshKey();
    sshKeyPairDAO.upsertSshKeyPair(sshKey);
    var externalUser = "foo@gmail.com";
    var pair = getRSAEncodedKeyPair(externalUser);
    var userId = sshKey.getUserId();
    var newSshKeyPair =
        new SshKeyPair.Builder()
            .userId(userId)
            .type(SshKeyPairType.GITHUB)
            .externalUserEmail(externalUser)
            .privateKey(pair.getLeft())
            .publicKey(pair.getRight())
            .build();

    var storedSshKey =
        sshKeyPairService.putSshKeyPair(
            newSshKeyPair.getUserId(),
            newSshKeyPair.getType(),
            newSshKeyPair.getPrivateKey(),
            newSshKeyPair.getPublicKey(),
            newSshKeyPair.getExternalUserEmail());

    assertNotEquals(sshKey.withId(storedSshKey.getId()), storedSshKey);
    verifySshKeyPair(newSshKeyPair, storedSshKey);
  }

  private void verifySshKeyPair(SshKeyPair expectedSshKey, SshKeyPair actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
