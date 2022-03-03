package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.SshKeyType;
import bio.terra.externalcreds.models.SshKey;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SshKeyDaoTest extends BaseTest {

  @Autowired SshKeyDAO sshKeyDAO;

  private static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
  private static final SshKeyType DEFAULT_KEY_TYPE = SshKeyType.GITHUB;
  private static final String DEFAULT_EXTERNAL_USER_EMAIL = "foo@monkeyseesmonkeydo.com";

  @Nested
  class upsertKeyTest {
    @Test
    void upsertSshKey_upsertTwice_secondKeyOverrideTheFirstKey() {
      var sshKeyOne = createSshKey();
      var sshKeyTwo = createSshKey(DEFAULT_USER_ID, DEFAULT_KEY_TYPE, "bar@monkeyseesmonkeydo.com");

      sshKeyDAO.upsertSshKey(sshKeyOne);
      var storedSshKeyTwo = sshKeyDAO.upsertSshKey(sshKeyTwo);
      var loadedSshKeyTwoOptional = sshKeyDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE);

      assertPresent(loadedSshKeyTwoOptional);
      assertEquals(
          storedSshKeyTwo.withId(loadedSshKeyTwoOptional.get().getId()),
          loadedSshKeyTwoOptional.get());
    }

    @Test
    void upsertSshKey_addId() {
      var sshKey = createSshKey();
      var storedSshKey = sshKeyDAO.upsertSshKey(sshKey);

      assertEquals(sshKey.withId(storedSshKey.getId()), storedSshKey);
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
      assertEquals(
          savedSshKey.withId(loadedSshKeyOptional.get().getId()), loadedSshKeyOptional.get());
    }
  }

  @Nested
  class DeleteKeyTest {
    @Test
    void deleteSshKey_successfullyDeleted () {
    var sshKey = createSshKey();
    sshKeyDAO.upsertSshKey(sshKey);

    assertTrue(sshKeyDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));

    assertEmpty(sshKeyDAO.getSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
    assertFalse(sshKeyDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
  }

    @Test
    void deleteSshKey_keyNotExist_returnsFalse () {
    assertFalse(sshKeyDAO.deleteSecret(DEFAULT_USER_ID, DEFAULT_KEY_TYPE));
  }
  }

  private static SshKey createSshKey() {
    return createSshKey(DEFAULT_USER_ID, DEFAULT_KEY_TYPE, DEFAULT_EXTERNAL_USER_EMAIL);
  }

  private static SshKey createSshKey(String userId, SshKeyType keyType, String externalUserEmail) {
    String key =
        "-----BEGIN OPENSSH PRIVATE KEY-----\n"
            + "abcde12345/+xXXXYZ//890=\n"
            + "-----END OPENSSH PRIVATE KEY-----";
    return new SshKey.Builder()
        .type(keyType)
        .secretContent(key)
        .userId(userId)
        .externalUserEmail(externalUserEmail)
        .build();
  }
}
