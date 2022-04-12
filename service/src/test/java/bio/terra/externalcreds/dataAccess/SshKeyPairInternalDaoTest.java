package bio.terra.externalcreds.dataAccess;

import static bio.terra.externalcreds.TestUtils.createRandomGithubSshKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ExternalCredsConfigInterface.KmsConfiguration;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
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
  private static final KmsConfiguration KMS_CONFIGURATION =
      new KmsConfiguration() {
        @Override
        public String getServiceGoogleProject() {
          return "projectId";
        }

        @Override
        public String getKeyRingId() {
          return "key_ring";
        }

        @Override
        public String getKeyId() {
          return "key_id";
        }

        @Override
        public String getKeyRingLocation() {
          return "us-central1";
        }

        @Override
        public Duration getSshKeyPairRefreshDuration() {
          return Duration.ZERO;
        }
      };

  @Nested
  class UpsertKeyPair {

    @Test
    void testUpsertTwiceWithSameUserId() throws NoSuchAlgorithmException, IOException {
      var externalUserEmail = "bar@monkeyseesmonkeydo.com";
      var sshKeyOne = createRandomGithubSshKey();
      var sshKeyTwo = sshKeyOne.withExternalUserEmail(externalUserEmail);
      setUpDefaultKmsEncryptDecryptHelperMock(sshKeyOne.getPrivateKey());
      setUpDefaultKmsEncryptDecryptHelperMock(sshKeyTwo.getPrivateKey());

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
      setUpDefaultKmsEncryptDecryptHelperMock(sshKeyOne.getPrivateKey());
      setUpDefaultKmsEncryptDecryptHelperMock(sshKeyTwo.getPrivateKey());
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
      setUpDefaultKmsEncryptDecryptHelperMock(sshKey.getPrivateKey());
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
      setUpDefaultKmsEncryptDecryptHelperMock(sshKey.getPrivateKey());
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      var loadedSshKeyOptional = sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType());

      assertPresent(loadedSshKeyOptional);
      verifySshKeyPair(sshKey, loadedSshKeyOptional.get());
    }

    @Test
    void testGetPreviouslyUnencryptedKeyPair() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      setUpDefaultKmsEncryptDecryptHelperMock(sshKey.getPrivateKey());
      sshKeyPairDAO.upsertSshKeyPair(sshKey);
      when(externalCredsConfig.getKmsConfiguration()).thenReturn(Optional.of(KMS_CONFIGURATION));
      Mockito.clearInvocations(kmsEncryptDecryptHelper);

      var loadedSshKeyOptional = sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType());
      assertPresent(loadedSshKeyOptional);
      verifySshKeyPair(sshKey, loadedSshKeyOptional.get());
      verifyNoInteractions(kmsEncryptDecryptHelper);
    }
  }

  @Nested
  @TestComponent
  class EncryptAndDecryptSshKeyPair {

    @Test
    void testGetDecryptedKeyPair() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      var cypheredKey = "jfidosruewr1k=";
      when(externalCredsConfig.getKmsConfiguration()).thenReturn(Optional.of(KMS_CONFIGURATION));
      when(kmsEncryptDecryptHelper.encryptSymmetric(sshKey.getPrivateKey()))
          .thenReturn(cypheredKey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(cypheredKey))
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
      assertEquals(cypheredKey, privateKey);
      assertPresent(loadedSshKeyOptional);
      verifySshKeyPair(sshKey, loadedSshKeyOptional.get());
    }
  }

  @Nested
  class DeleteKeyPair {
    @Test
    void testDeleteSshKeyPair() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      setUpDefaultKmsEncryptDecryptHelperMock(sshKey.getPrivateKey());
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
      setUpDefaultKmsEncryptDecryptHelperMock(sshKey.getPrivateKey());
      sshKeyPairDAO.upsertSshKeyPair(sshKey);

      assertFalse(sshKeyPairDAO.deleteSshKeyPairIfExists(sshKey.getUserId(), SshKeyPairType.AZURE));

      assertTrue(sshKeyPairDAO.deleteSshKeyPairIfExists(sshKey.getUserId(), sshKey.getType()));
    }
  }

  private void verifySshKeyPair(
      SshKeyPairInternal expectedSshKey, SshKeyPairInternal actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }

  private void setUpDefaultKmsEncryptDecryptHelperMock(String privateKey) {
    when(kmsEncryptDecryptHelper.encryptSymmetric(privateKey)).thenReturn(privateKey);
    when(kmsEncryptDecryptHelper.decryptSymmetric(privateKey)).thenReturn(privateKey);
  }
}
