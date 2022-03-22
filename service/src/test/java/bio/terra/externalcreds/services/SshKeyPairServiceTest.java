package bio.terra.externalcreds.services;

import static bio.terra.externalcreds.TestUtils.createRandomGithubSshKey;
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
  void generateSshKey() {
    var userId = UUID.randomUUID().toString();
    var externalUser = "foo@gmail.com";
    var sshKeyPair =
        sshKeyPairService.generateSshKeyPair(userId, externalUser, SshKeyPairType.GITHUB);

    var loadedSshKeyPair = sshKeyPairService.getSshKeyPair(userId, SshKeyPairType.GITHUB);
    verifySshKeyPair(sshKeyPair, loadedSshKeyPair.get());
  }

  @Test
  void generateAnotherSshKey() {
    var userId = UUID.randomUUID().toString();
    var externalUser = "foo@gmail.com";
    var sshKeyPair =
        sshKeyPairService.generateSshKeyPair(userId, externalUser, SshKeyPairType.GITHUB);

    var externalUserTwo = "bar@gmail.com";
    var sshKeyPair2 =
        sshKeyPairService.generateSshKeyPair(userId, externalUserTwo, SshKeyPairType.GITHUB);

    var loadedSshKeyPair = sshKeyPairService.getSshKeyPair(userId, SshKeyPairType.GITHUB);
    verifySshKeyPair(sshKeyPair2, loadedSshKeyPair.get());
    assertNotEquals(sshKeyPair.withId(loadedSshKeyPair.get().getId()), loadedSshKeyPair.get());
  }

  private void verifySshKeyPair(SshKeyPair expectedSshKey, SshKeyPair actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
