package bio.terra.externalcreds.dataAccess;

import static bio.terra.externalcreds.SshKeyPairTestUtils.createRandomGithubSshKey;
import static bio.terra.externalcreds.SshKeyPairTestUtils.getFakeKmsConfiguration;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SshKeyPairInternalDaoTest extends BaseTest {

  @Autowired SshKeyPairDAO sshKeyPairDAO;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;
  @MockBean ExternalCredsConfig externalCredsConfig;
  @MockBean KmsEncryptDecryptHelper kmsEncryptDecryptHelper;

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
      when(externalCredsConfig.getKmsConfiguration())
          .thenReturn(Optional.of(getFakeKmsConfiguration(Duration.ofDays(60))));
      when(kmsEncryptDecryptHelper.encryptSymmetric(sshKey.getPrivateKey()))
          .thenReturn(cypheredkey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(cypheredkey))
          .thenReturn(sshKey.getPrivateKey());

      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      var loadedSshKeyOptional = sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType());

      var namedParameters =
          new MapSqlParameterSource()
              .addValue("userId", sshKey.getUserId())
              .addValue("type", sshKey.getType().name());
      var resourceSelectSql =
          "SELECT private_key FROM ssh_key_pair WHERE user_id = :userId AND type = :type";
      var privateKey =
          DataAccessUtils.singleResult(
              jdbcTemplate.query(
                  resourceSelectSql, namedParameters, (rs, rowNum) -> rs.getString("private_key")));
      assertEquals(cypheredkey, privateKey);
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

      var sshKey = createRandomGithubSshKey();
      var cypheredkey = "jfidosruewr1k=";
      when(externalCredsConfig.getKmsConfiguration())
          .thenReturn(Optional.of(getFakeKmsConfiguration(Duration.ofSeconds(15))));
      when(kmsEncryptDecryptHelper.encryptSymmetric(sshKey.getPrivateKey()))
          .thenReturn(cypheredkey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(cypheredkey))
          .thenReturn(sshKey.getPrivateKey());
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      // Go to the future 30 seconds from now.
      var expiredSshKeys =
          sshKeyPairDAO.getExpiredOrUnEncryptedSshKeyPair(
              Timestamp.from(Instant.now().plus(Duration.ofSeconds(30))));
      assertEquals(1, expiredSshKeys.size());
      verifySshKeyPair(sshKey, expiredSshKeys.get(0));
    }

    @Test
    void testReEncryptKey() throws NoSuchAlgorithmException, IOException {
      // Delete all the row in the ssh_key_pair data table.
      SshKeyPairTestUtils.cleanUp(jdbcTemplate);

      var cypheredkey = "jfidosruewr1k=";
      when(externalCredsConfig.getKmsConfiguration())
          .thenReturn(Optional.of(getFakeKmsConfiguration(Duration.ofSeconds(10))));
      var sshKeyPair = createRandomGithubSshKey();
      when(kmsEncryptDecryptHelper.encryptSymmetric(sshKeyPair.getPrivateKey()))
          .thenReturn(cypheredkey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(cypheredkey))
          .thenReturn(sshKeyPair.getPrivateKey());
      sshKeyPairDAO.upsertSshKeyPair(sshKeyPair);

      var cypheredKey2 = "3mi2k31-&3";
      when(kmsEncryptDecryptHelper.encryptSymmetric(sshKeyPair.getPrivateKey()))
          .thenReturn(cypheredKey2);
      var expiredSshKeyPair =
          sshKeyPairDAO.getExpiredOrUnEncryptedSshKeyPair(
              Timestamp.from(Instant.now().plus(Duration.ofSeconds(30))));
      verifySshKeyPair(sshKeyPair, expiredSshKeyPair.get(0));

      // Re-encrypt the ssh key.
      sshKeyPairDAO.upsertSshKeyPair(expiredSshKeyPair.get(0));
      // This time there should be no expired key.
      var emptyList =
          sshKeyPairDAO.getExpiredOrUnEncryptedSshKeyPair(Timestamp.from(Instant.now()));
      assertEquals(0, emptyList.size());

      var namedParameters =
          new MapSqlParameterSource()
              .addValue("userId", sshKeyPair.getUserId())
              .addValue("type", sshKeyPair.getType().name());
      var resourceSelectSql =
          "SELECT private_key FROM ssh_key_pair WHERE user_id = :userId AND type = :type";
      var privateKey =
          DataAccessUtils.singleResult(
              jdbcTemplate.query(
                  resourceSelectSql, namedParameters, (rs, rowNum) -> rs.getString("private_key")));
      // Verify that the key is re-encrypted.
      assertEquals(cypheredKey2, privateKey);
    }
  }

  private void verifySshKeyPair(
      SshKeyPairInternal expectedSshKey, SshKeyPairInternal actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
