package bio.terra.externalcreds.dataAccess;

import static bio.terra.externalcreds.SshKeyPairTestUtils.createRandomGithubSshKey;
import static bio.terra.externalcreds.SshKeyPairTestUtils.getDefaultFakeKmsConfiguration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.SshKeyPairTestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SshKeyPairInternalDaoTest extends BaseTest {

  @Autowired SshKeyPairDAO sshKeyPairDAO;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;
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
    void testGetPreviouslyUnencryptedKeyPair() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      when(externalCredsConfig.getKmsConfiguration()).thenReturn(getDefaultFakeKmsConfiguration());

      var loadedSshKeyOptional = sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType());
      assertPresent(loadedSshKeyOptional);
      verifySshKeyPair(sshKey, loadedSshKeyOptional.get());
    }
  }

  @Nested
  class DeleteKeyPair {
    @Test
    void testDeleteSshKeyPair() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      assertTrue(sshKeyPairDAO.deleteSshKeyPairIfExists(sshKey.getUserId(), sshKey.getType()));

      assertEmpty(sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType()));
      assertFalse(sshKeyPairDAO.deleteSshKeyPairIfExists(sshKey.getUserId(), sshKey.getType()));
    }

    @Test
    void testDeleteNonExistingSshKeyPair() {
      assertFalse(sshKeyPairDAO.deleteSshKeyPairIfExists("", DEFAULT_KEY_TYPE));
    }

    @Test
    void deleteSshKeyPairWithWrongType() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      assertFalse(sshKeyPairDAO.deleteSshKeyPairIfExists(sshKey.getUserId(), SshKeyPairType.AZURE));

      assertTrue(sshKeyPairDAO.deleteSshKeyPairIfExists(sshKey.getUserId(), sshKey.getType()));
    }
  }

  @Nested
  class ReEncryptKeys {
    @Test
    void testGetExpiringSshKeyPair() throws NoSuchAlgorithmException, IOException {
      // Delete all the row in the ssh_key_pair data table.
      SshKeyPairTestUtils.cleanUp(jdbcTemplate);

      var sshKey = createRandomGithubSshKey().withLastEncryptedTimestamp(Instant.now());
      var sshKey2 = createRandomGithubSshKey();
      // kms disabled, sshkey is not encrypted
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      // kms enabled, sshkey2 is encrypted
      when(externalCredsConfig.getKmsConfiguration()).thenReturn(getDefaultFakeKmsConfiguration());
      sshKeyPairDAO.upsertSshKeyPair(sshKey2);

      // refresh duration is set to 0, both keys are qualified for re-encryption.
      var expiredSshKeys = sshKeyPairDAO.getExpiredOrUnEncryptedSshKeyPair(Instant.now());
      assertEquals(2, expiredSshKeys.size());

      // refresh duration is set to 15 days, only the sshkey which is not encrypted is qualified
      // for re-encryption.
      var expiredSshKeys2 =
          sshKeyPairDAO.getExpiredOrUnEncryptedSshKeyPair(Instant.now().minus(Duration.ofDays(15)));
      verifySshKeyPair(sshKey2, expiredSshKeys2.get(0));
    }

    @Test
    void testKmsDisabledGetExpiringSshKey() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      // kms disabled, sshkey is not encrypted
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      var expiringSshkeys = sshKeyPairDAO.getExpiredOrUnEncryptedSshKeyPair(Instant.now());

      assertTrue(expiringSshkeys.isEmpty());
    }
  }

  private void verifySshKeyPair(
      SshKeyPairInternal expectedSshKey, SshKeyPairInternal actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
