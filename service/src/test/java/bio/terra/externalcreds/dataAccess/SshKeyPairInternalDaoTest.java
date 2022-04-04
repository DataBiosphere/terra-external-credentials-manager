package bio.terra.externalcreds.dataAccess;

import static bio.terra.externalcreds.TestUtils.createRandomGithubSshKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

    @Test
    void encryptKeyFails() {
      when(externalCredsConfig.getEnableKmsEncryption()).thenReturn(true);
      String location = "us-central1";
      when(externalCredsConfig.getKeyRingLocation()).thenReturn(Optional.of(location));
      String keyRing = "key_ring";
      when(externalCredsConfig.getKeyRingId()).thenReturn(Optional.of(keyRing));
      String encryptionKey = "encryption_key";
      when(externalCredsConfig.getKeyId()).thenReturn(Optional.of(encryptionKey));
      try (var utilsMock = Mockito.mockStatic(EncryptDecryptUtils.class)) {
        utilsMock
            .when(
                () ->
                    EncryptDecryptUtils.encryptSymmetric(
                        eq(externalCredsConfig.getServiceGoogleProject()),
                        eq(location),
                        eq(keyRing),
                        eq(encryptionKey),
                        any()))
            .thenThrow(new IOException("something went wrong, failed to encrypt"));
        assertThrows(
            ExternalCredsException.class,
            () -> sshKeyPairDAO.upsertSshKeyPair(createRandomGithubSshKey()));
      }
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
      String location = "us-central1";
      when(externalCredsConfig.getKeyRingLocation()).thenReturn(Optional.of(location));
      String keyRing = "key_ring";
      when(externalCredsConfig.getKeyRingId()).thenReturn(Optional.of(keyRing));
      String encryptionKey = "encryption_key";
      when(externalCredsConfig.getKeyId()).thenReturn(Optional.of(encryptionKey));
      try (var utilsMock = Mockito.mockStatic(EncryptDecryptUtils.class)) {
        utilsMock
            .when(
                () ->
                    EncryptDecryptUtils.encryptSymmetric(
                        eq(externalCredsConfig.getServiceGoogleProject()),
                        eq(location),
                        eq(keyRing),
                        eq(encryptionKey),
                        eq(sshKey.getPrivateKey())))
            .thenReturn(cypheredkey);
        utilsMock
            .when(
                () ->
                    EncryptDecryptUtils.decryptSymmetric(
                        eq(externalCredsConfig.getServiceGoogleProject()),
                        eq(location),
                        eq(keyRing),
                        eq(encryptionKey),
                        eq(cypheredkey)))
            .thenReturn(sshKey.getPrivateKey());

        sshKeyPairDAO.upsertSshKeyPair(sshKey);

        var loadedSshKeyOptional =
            sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType());

        assertPresent(loadedSshKeyOptional);
        verifySshKeyPair(sshKey, loadedSshKeyOptional.get());
      }
    }

    @Test
    void decryptKeyFails() throws NoSuchAlgorithmException, IOException {
      var sshKey = createRandomGithubSshKey();
      when(externalCredsConfig.getEnableKmsEncryption()).thenReturn(true);
      var cypheredkey = "jfidosruewr1k=";
      String location = "us-central1";
      when(externalCredsConfig.getKeyRingLocation()).thenReturn(Optional.of(location));
      String keyRing = "key_ring";
      when(externalCredsConfig.getKeyRingId()).thenReturn(Optional.of(keyRing));
      String encryptionKey = "encryption_key";
      when(externalCredsConfig.getKeyId()).thenReturn(Optional.of(encryptionKey));
      try (var utilsMock = Mockito.mockStatic(EncryptDecryptUtils.class)) {
        utilsMock
            .when(
                () ->
                    EncryptDecryptUtils.encryptSymmetric(
                        eq(externalCredsConfig.getServiceGoogleProject()),
                        eq(location),
                        eq(keyRing),
                        eq(encryptionKey),
                        eq(sshKey.getPrivateKey())))
            .thenReturn(cypheredkey);
        utilsMock
            .when(
                () ->
                    EncryptDecryptUtils.decryptSymmetric(
                        eq(externalCredsConfig.getServiceGoogleProject()),
                        eq(location),
                        eq(keyRing),
                        eq(encryptionKey),
                        eq(cypheredkey)))
            .thenThrow(new IOException("Something went wrong, decryption fails"));
        sshKeyPairDAO.upsertSshKeyPair(sshKey);
        assertThrows(
            ExternalCredsException.class,
            () -> sshKeyPairDAO.getSshKeyPair(sshKey.getUserId(), sshKey.getType()));
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
