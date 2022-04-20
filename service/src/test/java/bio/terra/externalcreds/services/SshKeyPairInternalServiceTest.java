package bio.terra.externalcreds.services;

import static bio.terra.externalcreds.SshKeyPairTestUtils.createRandomGithubSshKey;
import static bio.terra.externalcreds.SshKeyPairTestUtils.getDefaultFakeKmsConfiguration;
import static bio.terra.externalcreds.SshKeyPairTestUtils.getFakeKmsConfiguration;
import static bio.terra.externalcreds.SshKeyPairTestUtils.getRSAEncodedKeyPair;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
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
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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

      when(kmsEncryptDecryptHelper.encryptSymmetric(
              pair.getLeft().getBytes(StandardCharsets.UTF_8)))
          .thenReturn(pair.getLeft().getBytes(StandardCharsets.UTF_8));
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
              .privateKey(pair.getLeft().getBytes(StandardCharsets.UTF_8))
              .publicKey(pair.getRight())
              .build();
      verifySshKeyPair(sshKeyPairExpected, storedSshKey);
    }

    @Test
    void updateSshKey() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      var keyType = SshKeyPairType.GITHUB;
      when(kmsEncryptDecryptHelper.encryptSymmetric(sshKey.getPrivateKey()))
          .thenReturn(sshKey.getPrivateKey());
      sshKeyPairDAO.upsertSshKeyPair(sshKey);
      var externalUser = "foo@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);
      var userId = sshKey.getUserId();

      var newSshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      when(kmsEncryptDecryptHelper.encryptSymmetric(
              pair.getLeft().getBytes(StandardCharsets.UTF_8)))
          .thenReturn(pair.getLeft().getBytes(StandardCharsets.UTF_8));
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, newSshKeyPair);

      var newSshKeyPairExpected =
          new SshKeyPairInternal.Builder()
              .userId(userId)
              .type(SshKeyPairType.GITHUB)
              .externalUserEmail(externalUser)
              .privateKey(pair.getLeft().getBytes(StandardCharsets.UTF_8))
              .publicKey(pair.getRight())
              .build();
      assertNotEquals(sshKey.withId(storedSshKey.getId()), storedSshKey);
      verifySshKeyPair(newSshKeyPairExpected, storedSshKey);
    }

    @Test
    void updateSshKeyWithEncryption() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      var keyType = SshKeyPairType.GITHUB;
      when(kmsEncryptDecryptHelper.encryptSymmetric(sshKey.getPrivateKey()))
          .thenReturn(sshKey.getPrivateKey());
      sshKeyPairDAO.upsertSshKeyPair(sshKey);
      when(config.getKmsConfiguration()).thenReturn(getFakeKmsConfiguration(Duration.ZERO));
      var externalUser = "foo@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);
      var userId = sshKey.getUserId();

      var newSshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      when(kmsEncryptDecryptHelper.encryptSymmetric(
              pair.getLeft().getBytes(StandardCharsets.UTF_8)))
          .thenReturn(RandomStringUtils.random(10).getBytes(StandardCharsets.UTF_8));
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, newSshKeyPair);

      var newSshKeyPairExpected =
          new SshKeyPairInternal.Builder()
              .userId(userId)
              .type(SshKeyPairType.GITHUB)
              .externalUserEmail(externalUser)
              .privateKey(pair.getLeft().getBytes(StandardCharsets.UTF_8))
              .publicKey(pair.getRight())
              .build();
      assertNotEquals(sshKey.withId(storedSshKey.getId()), storedSshKey);
      verifySshKeyPair(newSshKeyPairExpected, storedSshKey);
      verify(kmsEncryptDecryptHelper, times(1))
          .encryptSymmetric(pair.getLeft().getBytes(StandardCharsets.UTF_8));
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
      SshKeyPairTestUtils.cleanUp(jdbcTemplate);

      when(config.getKmsConfiguration()).thenReturn(getDefaultFakeKmsConfiguration());
      var userId = UUID.randomUUID().toString();
      var keyType = SshKeyPairType.GITHUB;
      var externalUser = "renecrypt@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);

      var sshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      var cypheredKey = "ji32o10!2".getBytes(StandardCharsets.UTF_8);
      when(kmsEncryptDecryptHelper.encryptSymmetric(
              pair.getLeft().getBytes(StandardCharsets.UTF_8)))
          .thenReturn(cypheredKey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(cypheredKey))
          .thenReturn(pair.getLeft().getBytes(StandardCharsets.UTF_8));
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, sshKeyPair);

      sshKeyPairService.reEncryptExpiringSshKeyPairs();
      var loadedSshkeyPair = sshKeyPairService.getSshKeyPair(userId, keyType);
      verifySshKeyPair(storedSshKey, loadedSshkeyPair);
      // encrypt should be called twice as the key needs to be re-encrypted.
      verify(kmsEncryptDecryptHelper, times(2))
          .encryptSymmetric(pair.getLeft().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void notReEncryptSshKey() throws NoSuchAlgorithmException, IOException {
      SshKeyPairTestUtils.cleanUp(jdbcTemplate);
      var userId = UUID.randomUUID().toString();
      var keyType = SshKeyPairType.GITHUB;
      var externalUser = "renecrypt@gmail.com";
      var cypheredKey = "ji32o10!2".getBytes(StandardCharsets.UTF_8);
      var pair = getRSAEncodedKeyPair(externalUser);

      var sshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      when(kmsEncryptDecryptHelper.encryptSymmetric(
              pair.getLeft().getBytes(StandardCharsets.UTF_8)))
          .thenReturn(cypheredKey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(cypheredKey))
          .thenReturn(pair.getLeft().getBytes(StandardCharsets.UTF_8));
      when(config.getKmsConfiguration()).thenReturn(getFakeKmsConfiguration(Duration.ofDays(90)));
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, sshKeyPair);
      verify(kmsEncryptDecryptHelper, times(1))
          .encryptSymmetric(pair.getLeft().getBytes(StandardCharsets.UTF_8));
      clearInvocations(kmsEncryptDecryptHelper);

      // re-encryption should not re-encrypt sshKeyPair because we just put it in.
      sshKeyPairService.reEncryptExpiringSshKeyPairs();
      var loadedSshkeyPair = sshKeyPairService.getSshKeyPair(userId, keyType);
      verifySshKeyPair(storedSshKey, loadedSshkeyPair);
      verify(kmsEncryptDecryptHelper, times(0))
          .encryptSymmetric(pair.getLeft().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void encryptPreviouslyUnencryptedKey() throws NoSuchAlgorithmException, IOException {
      SshKeyPairTestUtils.cleanUp(jdbcTemplate);

      var userId = UUID.randomUUID().toString();
      var keyType = SshKeyPairType.GITHUB;
      var externalUser = "foo@gmail.com";
      var pair = getRSAEncodedKeyPair(externalUser);
      when(kmsEncryptDecryptHelper.encryptSymmetric(
              pair.getLeft().getBytes(StandardCharsets.UTF_8)))
          .thenReturn(pair.getLeft().getBytes(StandardCharsets.UTF_8));
      var sshKeyPair =
          new SshKeyPair()
              .privateKey(pair.getLeft())
              .publicKey(pair.getRight())
              .externalUserEmail(externalUser);
      var storedSshKey = sshKeyPairService.putSshKeyPair(userId, keyType, sshKeyPair);
      clearInvocations(kmsEncryptDecryptHelper);

      when(config.getKmsConfiguration()).thenReturn(getFakeKmsConfiguration(Duration.ofDays(90)));
      // Even when KMS config is enabled, if the key is not encrypted, we don't attempt to decrypt
      // it when fetching it from the database. Thus we don't need to mock `kmsEncryptDecryptHelper`
      // here.
      var loadedSshKey = sshKeyPairService.getSshKeyPair(userId, keyType);
      verifySshKeyPair(storedSshKey, loadedSshKey);

      var cypheredKey = "ji32o10!2".getBytes(StandardCharsets.UTF_8);
      when(kmsEncryptDecryptHelper.encryptSymmetric(
              pair.getLeft().getBytes(StandardCharsets.UTF_8)))
          .thenReturn(cypheredKey);
      when(kmsEncryptDecryptHelper.decryptSymmetric(cypheredKey))
          .thenReturn(pair.getLeft().getBytes(StandardCharsets.UTF_8));
      sshKeyPairService.reEncryptExpiringSshKeyPairs();
      verify(kmsEncryptDecryptHelper, times(1))
          .encryptSymmetric(pair.getLeft().getBytes(StandardCharsets.UTF_8));

      loadedSshKey = sshKeyPairService.getSshKeyPair(userId, keyType);
      verifySshKeyPair(storedSshKey, loadedSshKey);
    }
  }

  private void verifySshKeyPair(
      SshKeyPairInternal expectedSshKey, SshKeyPairInternal actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
