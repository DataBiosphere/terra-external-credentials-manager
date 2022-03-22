package bio.terra.externalcreds.services;

import static bio.terra.externalcreds.TestUtils.createRandomGithubSshKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
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
  void generateSshKeyPair() {
    var userId = "foo";
    var userEmail = "foo@gmail.com";
    var keyPairType = SshKeyPairType.GITHUB;
    var sshKeyPairInternal = sshKeyPairService.generateSshKeyPair(userId, userEmail, keyPairType);

    assertEquals(userEmail, sshKeyPairInternal.getExternalUserEmail());
    assertEquals(userId, sshKeyPairInternal.getUserId());
    assertEquals(keyPairType, sshKeyPairInternal.getType());

    var loadedSshKey = sshKeyPairService.getSshKeyPair(userId, keyPairType);
    verifySshKeyPair(sshKeyPairInternal, loadedSshKey.get());
  }

  private void verifySshKeyPair(
      SshKeyPairInternal expectedSshKey, SshKeyPairInternal actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
