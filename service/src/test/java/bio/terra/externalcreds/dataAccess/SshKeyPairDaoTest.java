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
  class upsertKeyTest {
    @Test
    void upsertSshKey_upsertTwice_secondKeyOverrideTheFirstKey() {
      var externalUserEmail = "bar@monkeyseesmonkeydo.com";
      var sshKeyOne = createDefaultSshKey();
      var sshKeyTwo = createSshKey(DEFAULT_USER_ID, DEFAULT_KEY_TYPE, externalUserEmail);

      sshKeyPairDAO.upsertSshKey(sshKeyOne);
      sshKeyPairDAO.upsertSshKey(sshKeyTwo);
      var loadedSshKeyTwoOptional = sshKeyPairDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);

      assertPresent(loadedSshKeyTwoOptional);
      assertEquals(externalUserEmail, loadedSshKeyTwoOptional.get().getExternalUserEmail());
    }

    @Test
    void upsertSshKey_differentUser_doNotOverride() {
      var userId = UUID.randomUUID().toString();
      var externalUserEmail = "bar@monkeyseesmonkeydo.com";
      var sshKeyOne = createDefaultSshKey();
      var sshKeyTwo = createSshKey(userId, DEFAULT_KEY_TYPE, externalUserEmail);
      sshKeyPairDAO.upsertSshKey(sshKeyOne);
      sshKeyPairDAO.upsertSshKey(sshKeyTwo);

      var loadedKeyOne = sshKeyPairDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);
      var loadedKeyTwo = sshKeyPairDAO.getSecret(userId, DEFAULT_KEY_TYPE);

      assertPresent(loadedKeyOne);
      assertPresent(loadedKeyTwo);
      verifySshKeyPair(loadedKeyOne.get());
      assertEquals(userId, loadedKeyTwo.get().getUserId());
      assertEquals(externalUserEmail, loadedKeyTwo.get().getExternalUserEmail());
      assertEquals(DEFAULT_KEY_TYPE, loadedKeyTwo.get().getType());
      assertEquals(DEFAULT_PUBLIC_KEY, loadedKeyTwo.get().getPublicKey());
      assertEquals(DEFAULT_PRIVATE_KEY, loadedKeyTwo.get().getPrivateKey());
    }

    @Test
    void upsertSshKey_addId() {
      var storedSshKey = sshKeyPairDAO.upsertSshKey(createDefaultSshKey());

      verifySshKeyPair(storedSshKey);
    }
  }

  @Nested
  class GetSshKeyTest {
    @Test
    void getSshKey_noUserId() {
      var empty = sshKeyPairDAO.getSecret("", DEFAULT_KEY_TYPE);

      assertEmpty(empty);
    }

    @Test
    void getSshKey() {
      var sshKey = createDefaultSshKey();
      sshKeyPairDAO.upsertSshKey(sshKey);

      var loadedSshKeyOptional = sshKeyPairDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);

      assertPresent(loadedSshKeyOptional);
      verifySshKeyPair(loadedSshKeyOptional.get());
    }
  }

  @Nested
  class DeleteKeyTest {
    @Test
    void deleteSshKey_successfullyDeleted() {
      sshKeyPairDAO.upsertSshKey(createDefaultSshKey());

      assertTrue(sshKeyPairDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));

      assertEmpty(sshKeyPairDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
      assertFalse(sshKeyPairDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
    }

    @Test
    void deleteSshKey_keyNotExist_returnsFalse() {
      assertFalse(sshKeyPairDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
    }

    @Test
    void deleteSshKey_specifyWrongType_returnsFalse() {
      sshKeyPairDAO.upsertSshKey(createDefaultSshKey());

      assertFalse(sshKeyPairDAO.deleteSecret(DEFAULT_USER_ID, SshKeyPairType.AZURE));

      assertTrue(sshKeyPairDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
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

  private void verifySshKeyPair(SshKeyPair storedSshKey) {
    assertEquals(DEFAULT_USER_ID, storedSshKey.getUserId());
    assertEquals(DEFAULT_KEY_TYPE, storedSshKey.getType());
    assertEquals(DEFAULT_EXTERNAL_USER_EMAIL, storedSshKey.getExternalUserEmail());
    assertEquals(DEFAULT_PRIVATE_KEY, storedSshKey.getPrivateKey());
    assertEquals(DEFAULT_PUBLIC_KEY, storedSshKey.getPublicKey());
  }
}
