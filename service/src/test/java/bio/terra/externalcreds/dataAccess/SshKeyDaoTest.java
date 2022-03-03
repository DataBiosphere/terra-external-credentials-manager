package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.SshKeyType;
import bio.terra.externalcreds.models.SshKeyPair;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SshKeyDaoTest extends BaseTest {

  @Autowired SshKeyDAO sshKeyDAO;

  private static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
  private static final SshKeyType DEFAULT_KEY_TYPE = SshKeyType.GITHUB;
  private static final String DEFAULT_EXTERNAL_USER_EMAIL = "foo@monkeyseesmonkeydo.com";
  private static final String DEFAULT_PRIVATE_KEY =
      "-----BEGIN OPENSSH PRIVATE KEY-----\n"
          + "abcde12345/+xXXXYZ//890=\n"
          + "-----END OPENSSH PRIVATE KEY-----";
  private static final String DEFAULT_PUBLIC_KEY =
      "ssh-ed25519 AAAA12345 " + DEFAULT_EXTERNAL_USER_EMAIL;

  @Nested
  class upsertKeyTest {
    @Test
    void upsertSshKey_upsertTwice_secondKeyOverrideTheFirstKey() {
      var externalUserEmail = "bar@monkeyseesmonkeydo.com";
      var sshKeyOne = createSshKey();
      var sshKeyTwo = createSshKey(DEFAULT_USER_ID, DEFAULT_KEY_TYPE, externalUserEmail);

      sshKeyDAO.upsertSshKey(sshKeyOne);
      sshKeyDAO.upsertSshKey(sshKeyTwo);
      var loadedSshKeyTwoOptional = sshKeyDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);

      assertPresent(loadedSshKeyTwoOptional);
      assertEquals(externalUserEmail, loadedSshKeyTwoOptional.get().getExternalUserEmail());
    }

    @Test
    void upsertSshKey_addId() {
      var sshKey = createSshKey();
      var storedSshKey = sshKeyDAO.upsertSshKey(sshKey);

      verifySshKeyPair(storedSshKey);
    }
  }

  @Nested
  class GetSshKeyTest {
    @Test
    void getSshKey_noUserId() {
      var empty = sshKeyDAO.getSecret("", DEFAULT_KEY_TYPE);

      assertEmpty(empty);
    }

    @Test
    void getSshKey() {
      var sshKey = createSshKey();
      var savedSshKey = sshKeyDAO.upsertSshKey(sshKey);

      var loadedSshKeyOptional = sshKeyDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);

      assertPresent(loadedSshKeyOptional);
      verifySshKeyPair(loadedSshKeyOptional.get());
    }
  }

  @Nested
  class DeleteKeyTest {
    @Test
    void deleteSshKey_successfullyDeleted() {
      var sshKey = createSshKey();
      sshKeyDAO.upsertSshKey(sshKey);

      assertTrue(sshKeyDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));

      assertEmpty(sshKeyDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
      assertFalse(sshKeyDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
    }

    @Test
    void deleteSshKey_keyNotExist_returnsFalse() {
      assertFalse(sshKeyDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
    }
  }

  private static SshKeyPair createSshKey() {
    return createSshKey(DEFAULT_USER_ID, DEFAULT_KEY_TYPE, DEFAULT_EXTERNAL_USER_EMAIL);
  }

  private static SshKeyPair createSshKey(
      String userId, SshKeyType keyType, String externalUserEmail) {
    return new SshKeyPair.Builder()
        .type(keyType)
        .privateKey(DEFAULT_PRIVATE_KEY)
        .publicKey(DEFAULT_PUBLIC_KEY)
        .userId(userId)
        .externalUserEmail(externalUserEmail)
        .build();
  }

  private void verifySshKeyPair(SshKeyPair storedSshKey) {
    assertEquals(DEFAULT_USER_ID, storedSshKey.getUserId());
    assertEquals(DEFAULT_KEY_TYPE, storedSshKey.getType());
    assertEquals(DEFAULT_EXTERNAL_USER_EMAIL, storedSshKey.getExternalUserEmail());
    assertEquals(DEFAULT_PRIVATE_KEY, storedSshKey.getPrivateKey());
    assertEquals(DEFAULT_PUBLIC_KEY, storedSshKey.getPublicKey());
  }
}
