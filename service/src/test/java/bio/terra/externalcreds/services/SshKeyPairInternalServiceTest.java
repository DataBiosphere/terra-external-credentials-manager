package bio.terra.externalcreds.services;

import static bio.terra.externalcreds.SshKeyPairTestUtils.createRandomGithubSshKey;
import static bio.terra.externalcreds.SshKeyPairTestUtils.getFakeKmsConfiguration;
import static bio.terra.externalcreds.SshKeyPairTestUtils.getRSAEncodedKeyPair;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.SshKeyPairTestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.KmsEncryptDecryptHelper;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class SshKeyPairInternalServiceTest extends BaseTest {

  @Autowired SshKeyPairService sshKeyPairService;
  @Autowired SshKeyPairDAO sshKeyPairDAO;

  @Test
  void getSshKeyPair() throws NoSuchAlgorithmException, IOException {
    var sshKey = createRandomGithubSshKey();
    sshKeyPairDAO.upsertSshKeyPair(sshKey);

    var loadedSshKey = sshKeyPairService.getSshKeyPair(sshKey.getUserId(), SshKeyPairType.GITHUB);

    verifySshKeyPair(sshKey, loadedSshKey);
  }

  @Test
  void getSshKeyPairKeyNotFound() {
    assertThrows(
        NotFoundException.class,
        () -> sshKeyPairService.getSshKeyPair(RandomStringUtils.random(5), SshKeyPairType.GITHUB));
  }

  @Test
  void deleteSshKeyPair() throws NoSuchAlgorithmException, IOException {
    var sshKey = createRandomGithubSshKey();
    sshKeyPairDAO.upsertSshKeyPair(sshKey);
  }

  @Test
  void deleteSshKeyPairKeyNotFound() {
    assertThrows(
        NotFoundException.class,
        () ->
            sshKeyPairService.deleteSshKeyPair(RandomStringUtils.random(5), SshKeyPairType.GITHUB));
  }

  @Nested
  @TestComponent
  class putSshKeyPair {

    @Autowired SshKeyPairService sshKeyPairService;
    @Autowired SshKeyPairDAO sshKeyPairDAO;
    @Autowired NamedParameterJdbcTemplate jdbcTemplate;

    @MockBean ExternalCredsConfig config;
    @MockBean KmsEncryptDecryptHelper kmsEncryptDecryptHelper;

    @Test
    void putSshKey() throws NoSuchAlgorithmException, IOException {
      var userId = UUID.randomUUID().toString();
      var keyType = SshKeyPairType.GITHUB;
      var externalUser = "foo@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);

      var sshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, sshKeyPair);

      var sshKeyPairExpected =
          new SshKeyPairInternal.Builder()
              .userId(userId)
              .type(keyType)
              .externalUserEmail(externalUser)
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .build();
      verifySshKeyPair(sshKeyPairExpected, storedSshKey);
    }

    @Test
    void updateSshKey() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      var keyType = SshKeyPairType.GITHUB;
      sshKeyPairDAO.upsertSshKeyPair(sshKey);
      var externalUser = "foo@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);
      var userId = sshKey.getUserId();

      var newSshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, newSshKeyPair);

      var newSshKeyPairExpected =
          new SshKeyPairInternal.Builder()
              .userId(userId)
              .type(SshKeyPairType.GITHUB)
              .externalUserEmail(externalUser)
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .build();
      assertNotEquals(sshKey.withId(storedSshKey.getId()), storedSshKey);
      verifySshKeyPair(newSshKeyPairExpected, storedSshKey);
    }

    @Test
    void updateSshKeyWithEncryption() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      var keyType = SshKeyPairType.GITHUB;
      sshKeyPairDAO.upsertSshKeyPair(sshKey);
      when(config.getKmsConfiguration())
          .thenReturn(Optional.of(getFakeKmsConfiguration(Duration.ZERO)));
      var externalUser = "foo@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);
      var userId = sshKey.getUserId();

      var newSshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      when(kmsEncryptDecryptHelper.encryptSymmetric(eq(pair.getLeft())))
          .thenReturn(RandomStringUtils.random(10));
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, newSshKeyPair);

      var newSshKeyPairExpected =
          new SshKeyPairInternal.Builder()
              .userId(userId)
              .type(SshKeyPairType.GITHUB)
              .externalUserEmail(externalUser)
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .build();
      assertNotEquals(sshKey.withId(storedSshKey.getId()), storedSshKey);
      verifySshKeyPair(newSshKeyPairExpected, storedSshKey);
      verify(kmsEncryptDecryptHelper, times(1)).encryptSymmetric(eq(pair.getLeft()));
    }
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
    verifySshKeyPair(sshKeyPairInternal, loadedSshKey);
  }

  @Nested
  @TestComponent
  class ReEncryptSshKey {
    @Autowired SshKeyPairService sshKeyPairService;
    @Autowired SshKeyPairDAO sshKeyPairDAO;
    @Autowired NamedParameterJdbcTemplate jdbcTemplate;

    @MockBean ExternalCredsConfig config;
    @MockBean KmsEncryptDecryptHelper kmsEncryptDecryptHelper;

    @Test
    void reEncryptSshKey() throws NoSuchAlgorithmException, IOException {
      // Delete all the row in the ssh_key_pair data table. This is so that if there are other
      // un-encrypted or expired key in the database left over from other tests, they create noise
      // to the test as we will attempt to encrypt them as well.
      SshKeyPairTestUtils.cleanUp(jdbcTemplate);

      when(config.getKmsConfiguration())
          .thenReturn(Optional.of(getFakeKmsConfiguration(Duration.ZERO)));
      var userId = UUID.randomUUID().toString();
      var keyType = SshKeyPairType.GITHUB;
      var externalUser = "renecrypt@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);

      var sshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      var cypheredKey = "ji32o10!2";
      when(kmsEncryptDecryptHelper.encryptSymmetric(eq(pair.getLeft()))).thenReturn(cypheredKey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(eq(cypheredKey))).thenReturn(pair.getLeft());
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, sshKeyPair);

      sshKeyPairService.reEncryptExpiringSshKeyPairs();
      var loadedSshkeyPair = sshKeyPairService.getSshKeyPair(userId, keyType);
      verifySshKeyPair(storedSshKey, loadedSshkeyPair);
      // encrypt should be called twice as the key needs to be re-encrypted.
      verify(kmsEncryptDecryptHelper, times(2)).encryptSymmetric(eq(pair.getLeft()));
    }

    @Test
    void notReEncryptSshKey() throws NoSuchAlgorithmException, IOException {
      // Delete all the row in the ssh_key_pair data table. This is so that if there are other
      // un-encrypted or expired key in the database left over from other tests, they create noise
      // to the test as we will attempt to encrypt them as well.
      SshKeyPairTestUtils.cleanUp(jdbcTemplate);

      when(config.getKmsConfiguration())
          .thenReturn(Optional.of(getFakeKmsConfiguration(Duration.ofDays(60))));
      var userId = UUID.randomUUID().toString();
      var keyType = SshKeyPairType.GITHUB;
      var externalUser = "renecrypt@gmail.com";
      var cypheredKey = "ji32o10!2";
      var pair = getRSAEncodedKeyPair(externalUser);

      var sshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      when(kmsEncryptDecryptHelper.encryptSymmetric(eq(pair.getLeft()))).thenReturn(cypheredKey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(eq(cypheredKey))).thenReturn(pair.getLeft());
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, sshKeyPair);
      verify(kmsEncryptDecryptHelper, times(1)).encryptSymmetric(eq(pair.getLeft()));

      sshKeyPairService.reEncryptExpiringSshKeyPairs();
      var loadedSshkeyPair = sshKeyPairService.getSshKeyPair(userId, keyType);
      verifySshKeyPair(storedSshKey, loadedSshkeyPair);
      // encrypt should not be called again as the refresh duration is 60 days.
      verify(kmsEncryptDecryptHelper, times(1)).encryptSymmetric(eq(pair.getLeft()));
    }

    @Test
    void encryptPreviouslyUnencryptedKey() throws NoSuchAlgorithmException, IOException {
      // Delete all the row in the ssh_key_pair data table. This is so that if there are other
      // un-encrypted or expired key in the database left over from other tests, they create noise
      // to the test as we will attempt to encrypt them as well.
      SshKeyPairTestUtils.cleanUp(jdbcTemplate);

      var userId = UUID.randomUUID().toString();
      var keyType = SshKeyPairType.GITHUB;
      var externalUser = "foo@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);

      var sshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, sshKeyPair);

      when(config.getKmsConfiguration())
          .thenReturn(Optional.of(getFakeKmsConfiguration(Duration.ofDays(60))));
      // Even when KMS config is enabled, if the key is not encrypted, we don't attempt to decrypt
      // it when fetching it from the database. Thus we don't need to mock `kmsEncryptDecryptHelper`
      // here.
      var loadedSshKey = sshKeyPairService.getSshKeyPair(userId, keyType);
      verifySshKeyPair(storedSshKey, loadedSshKey);

      var cypheredKey = "ji32o10!2";
      when(kmsEncryptDecryptHelper.encryptSymmetric(eq(pair.getLeft()))).thenReturn(cypheredKey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(eq(cypheredKey))).thenReturn(pair.getLeft());
      sshKeyPairService.reEncryptExpiringSshKeyPairs();
      verify(kmsEncryptDecryptHelper, times(1)).encryptSymmetric(eq(pair.getLeft()));

      loadedSshKey = sshKeyPairService.getSshKeyPair(userId, keyType);
      verifySshKeyPair(storedSshKey, loadedSshKey);
    }
  }

  private void verifySshKeyPair(
      SshKeyPairInternal expectedSshKey, SshKeyPairInternal actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
