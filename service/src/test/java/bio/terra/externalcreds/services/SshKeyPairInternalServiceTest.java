package bio.terra.externalcreds.services;

import static bio.terra.externalcreds.TestUtils.createRandomGithubSshKey;
import static bio.terra.externalcreds.TestUtils.getRSAEncodedKeyPair;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SshKeyPairInternalServiceTest extends BaseTest {

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
    var keyType = SshKeyPairType.GITHUB;
    var externalUser = "foo@gmail.com";
    var pair = getRSAEncodedKeyPair(externalUser);

    var sshKeyPair =
        new SshKeyPair()
            .privateKey(pair.getLeft())
            .publicKey(pair.getRight())
            .externalUserEmail(externalUser);
    var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, sshKeyPair);

    var sshKeyPairExpected =
        new SshKeyPairInternal.Builder()
            .userId(userId)
            .type(keyType)
            .externalUserEmail(externalUser)
            .privateKey(pair.getLeft())
            .publicKey(pair.getRight())
            .build();
    verifySshKeyPair(sshKeyPairExpected, storedSshKey);
  }

  @Test
  void updateSshKey() throws NoSuchAlgorithmException, IOException {
    var sshKey = createRandomGithubSshKey();
    var keyType = SshKeyPairType.GITHUB;
    sshKeyPairDAO.upsertSshKeyPair(sshKey);
    var externalUser = "foo@gmail.com";
    var pair = getRSAEncodedKeyPair(externalUser);
    var userId = sshKey.getUserId();

    var newSshKeyPair =
        new SshKeyPair()
            .privateKey(pair.getLeft())
            .publicKey(pair.getRight())
            .externalUserEmail(externalUser);
    var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, newSshKeyPair);

    var newSshKeyPairExpected =
        new SshKeyPairInternal.Builder()
            .userId(userId)
            .type(SshKeyPairType.GITHUB)
            .externalUserEmail(externalUser)
            .privateKey(pair.getLeft())
            .publicKey(pair.getRight())
            .build();
    assertNotEquals(sshKey.withId(storedSshKey.getId()), storedSshKey);
    verifySshKeyPair(newSshKeyPairExpected, storedSshKey);
  }

  private void verifySshKeyPair(
      SshKeyPairInternal expectedSshKey, SshKeyPairInternal actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}