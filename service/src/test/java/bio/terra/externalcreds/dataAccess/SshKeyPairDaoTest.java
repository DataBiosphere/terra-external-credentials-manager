package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPair;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SshKeyPairDaoTest extends BaseTest {

  @Autowired SshKeyPairDAO sshKeyPairDAO;

  private static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
  private static final SshKeyPairType DEFAULT_KEY_TYPE = SshKeyPairType.GITHUB;
  private static final String DEFAULT_EXTERNAL_USER_EMAIL = "foo@monkeyseesmonkeydo.com";
  private static final String DEFAULT_PRIVATE_KEY =
      "-----BEGIN OPENSSH PRIVATE KEY-----\n"
          + "abcde12345/+xXXXYZ//890=\n"
          + "-----END OPENSSH PRIVATE KEY-----";
  private static final String DEFAULT_PUBLIC_KEY =
      "ssh-ed25519 AAAA12345 " + DEFAULT_EXTERNAL_USER_EMAIL;

  @Nested
  class UpsertKeyPair {
    @Test
    void testUpsertTwiceWithSameUserId() {
      var externalUserEmail = "bar@monkeyseesmonkeydo.com";
      var sshKeyOne = createDefaultSshKey();
      var sshKeyTwo = createSshKey(DEFAULT_USER_ID, DEFAULT_KEY_TYPE, externalUserEmail);

      sshKeyPairDAO.upsertSshKeyPair(sshKeyOne);
      sshKeyPairDAO.upsertSshKeyPair(sshKeyTwo);
      var loadedSshKeyTwo = sshKeyPairDAO.getSshKeyPair(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);

      assertPresent(loadedSshKeyTwo);
      verifySshKeyPair(sshKeyTwo, loadedSshKeyTwo.get());
    }

    @Test
    void testUpsertTwiceWithDifferentUserId() {
      var userId = UUID.randomUUID().toString();
      var externalUserEmail = "bar@monkeyseesmonkeydo.com";
      var sshKeyOne = createDefaultSshKey();
      var sshKeyTwo = createSshKey(userId, DEFAULT_KEY_TYPE, externalUserEmail);
      sshKeyPairDAO.upsertSshKeyPair(sshKeyOne);
      sshKeyPairDAO.upsertSshKeyPair(sshKeyTwo);

      var loadedKeyOne = sshKeyPairDAO.getSshKeyPair(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);
      var loadedKeyTwo = sshKeyPairDAO.getSshKeyPair(userId, DEFAULT_KEY_TYPE);

      assertPresent(loadedKeyOne);
      assertPresent(loadedKeyTwo);
      verifySshKeyPair(sshKeyOne, loadedKeyOne.get());
      verifySshKeyPair(sshKeyTwo, loadedKeyTwo.get());
    }

    @Test
    void upsertSshKey() {
      var sshKey = createDefaultSshKey();
      var storedSshKey = sshKeyPairDAO.upsertSshKeyPair(sshKey);

      verifySshKeyPair(sshKey, storedSshKey);
    }
  }

  @Nested
  class GetSshKeyPair {
    @Test
    void testGetSshKeyPairWithoutUserId() {
      var empty = sshKeyPairDAO.getSshKeyPair("", DEFAULT_KEY_TYPE);

      assertEmpty(empty);
    }

    @Test
    void testGetSshKeyPair() {
      var sshKey = createDefaultSshKey();
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      var loadedSshKeyOptional = sshKeyPairDAO.getSshKeyPair(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);

      assertPresent(loadedSshKeyOptional);
      verifySshKeyPair(sshKey, loadedSshKeyOptional.get());
    }
  }

  @Nested
  class DeleteKeyPair {
    @Test
    void testDeleteSshKeyPair() {
      sshKeyPairDAO.upsertSshKeyPair(createDefaultSshKey());

      assertTrue(sshKeyPairDAO.deleteSshKeyPair(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));

      assertEmpty(sshKeyPairDAO.getSshKeyPair(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
      assertFalse(sshKeyPairDAO.deleteSshKeyPair(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
    }

    @Test
    void testDeleteNonExistingSshKeyPair() {
      assertFalse(sshKeyPairDAO.deleteSshKeyPair(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
    }

    @Test
    void deleteSshKeyPairWithWrongType() {
      sshKeyPairDAO.upsertSshKeyPair(createDefaultSshKey());

      assertFalse(sshKeyPairDAO.deleteSshKeyPair(DEFAULT_USER_ID, SshKeyPairType.AZURE));

      assertTrue(sshKeyPairDAO.deleteSshKeyPair(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
    }
  }

  private static SshKeyPair createDefaultSshKey() {
    return createSshKey(DEFAULT_USER_ID, DEFAULT_KEY_TYPE, DEFAULT_EXTERNAL_USER_EMAIL);
  }

  private static SshKeyPair createSshKey(
      String userId, SshKeyPairType keyType, String externalUserEmail) {
    return new SshKeyPair.Builder()
        .type(keyType)
        .privateKey(DEFAULT_PRIVATE_KEY)
        .publicKey(DEFAULT_PUBLIC_KEY)
        .userId(userId)
        .externalUserEmail(externalUserEmail)
        .build();
  }

  private void verifySshKeyPair(SshKeyPair expectedSshKey, SshKeyPair actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId().orElse(-1)), actualSshKey);
  }
}
