package bio.terra.externalcreds.dataAccess;

import static bio.terra.externalcreds.TestUtils.createRandomGithubSshKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class SshKeyPairInternalDaoTest extends BaseTest {

  @Autowired SshKeyPairDAO sshKeyPairDAO;
  @MockBean ExternalCredsConfig externalCredsConfig;

  private static final SshKeyPairType DEFAULT_KEY_TYPE = SshKeyPairType.GITHUB;

  @Nested
  class UpsertKeyPair {
    @Test
    void testUpsertTwiceWithSameUserId() throws NoSuchAlgorithmException, IOException {
      var externalUserEmail = "bar@monkeyseesmonkeydo.com";
      var sshKeyOne = createRandomGithubSshKey();
      var sshKeyTwo = sshKeyOne.withExternalUserEmail(externalUserEmail);

      sshKeyPairDAO.upsertSshKeyPair(sshKeyOne);
      sshKeyPairDAO.upsertSshKeyPair(sshKeyTwo);
      var loadedSshKeyTwo = sshKeyPairDAO.getSshKeyPair(sshKeyOne.getUserId(), sshKeyOne.getType());

      assertPresent(loadedSshKeyTwo);
      verifySshKeyPair(sshKeyTwo, loadedSshKeyTwo.get());
    }

    @Test
    void testUpsertTwiceWithDifferentUserId() throws NoSuchAlgorithmException, IOException {
      var userId = UUID.randomUUID().toString();
      var externalUserEmail = "bar@monkeyseesmonkeydo.com";
      var sshKeyOne = createRandomGithubSshKey();
      var sshKeyTwo =
          createRandomGithubSshKey().withUserId(userId).withExternalUserEmail(externalUserEmail);
      sshKeyPairDAO.upsertSshKeyPair(sshKeyOne);
      sshKeyPairDAO.upsertSshKeyPair(sshKeyTwo);

      var loadedKeyOne = sshKeyPairDAO.getSshKeyPair(sshKeyOne.getUserId(), sshKeyOne.getType());
      var loadedKeyTwo = sshKeyPairDAO.getSshKeyPair(userId, sshKeyTwo.getType());

      assertPresent(loadedKeyOne);
      assertPresent(loadedKeyTwo);
      verifySshKeyPair(sshKeyOne, loadedKeyOne.get());
      verifySshKeyPair(sshKeyTwo, loadedKeyTwo.get());
    }

    @Test
    void upsertSshKey() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      var storedSshKey = sshKeyPairDAO.upsertSshKeyPair(sshKey);

      verifySshKeyPair(sshKey, storedSshKey);
    }
  }

  @Nested
  class GetSshKeyPairInternal {
    @Test
    void testGetSshKeyPairWithoutUserId() {
      var empty = sshKeyPairDAO.getSshKeyPair("", DEFAULT_KEY_TYPE);

      assertEmpty(empty);
    }

    @Test
    void testGetSshKeyPair() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      var loadedSshKeyOptional = sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType());

      assertPresent(loadedSshKeyOptional);
      verifySshKeyPair(sshKey, loadedSshKeyOptional.get());
    }

    @Test
    void testGetDecryptedKeyPair() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      var cypheredkey = "jfidosruewr1k=";
      when(externalCredsConfig.getEnableKmsEncryption()).thenReturn(true);
      try (var utilsMock =
          Mockito.mockStatic(EncryptDecryptUtils.class)) {
        utilsMock
            .when(
                () ->
                    EncryptDecryptUtils.encryptSymmetrtic(
                        any(), any(), any(), any(), sshKey.getPrivateKey()))
            .thenReturn(cypheredkey);
        utilsMock
            .when(
                () -> EncryptDecryptUtils.decryptSymmetric(any(), any(), any(), any(), cypheredkey))
            .thenReturn(sshKey.getPrivateKey());

        sshKeyPairDAO.upsertSshKeyPair(sshKey);

        var loadedSshKeyOptional =
            sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType());

        assertPresent(loadedSshKeyOptional);
        verifySshKeyPair(sshKey, loadedSshKeyOptional.get());
      }
    }
  }

  @Nested
  class DeleteKeyPair {
    @Test
    void testDeleteSshKeyPair() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      assertTrue(sshKeyPairDAO.deleteSshKeyPair(sshKey.getUserId(), sshKey.getType()));

      assertEmpty(sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType()));
      assertFalse(sshKeyPairDAO.deleteSshKeyPair(sshKey.getUserId(), sshKey.getType()));
    }

    @Test
    void testDeleteNonExistingSshKeyPair() {
      assertFalse(sshKeyPairDAO.deleteSshKeyPair("", DEFAULT_KEY_TYPE));
    }

    @Test
    void deleteSshKeyPairWithWrongType() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      assertFalse(sshKeyPairDAO.deleteSshKeyPair(sshKey.getUserId(), SshKeyPairType.AZURE));

      assertTrue(sshKeyPairDAO.deleteSshKeyPair(sshKey.getUserId(), sshKey.getType()));
    }
  }

  private void verifySshKeyPair(
      SshKeyPairInternal expectedSshKey, SshKeyPairInternal actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
