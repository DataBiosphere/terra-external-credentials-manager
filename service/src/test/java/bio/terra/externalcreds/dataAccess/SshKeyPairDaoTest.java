package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPair;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SshKeyPairDaoTest extends BaseTest {

  @Autowired SshKeyPairDAO sshKeyPairDAO;

  private static final SshKeyPairType DEFAULT_KEY_TYPE = SshKeyPairType.GITHUB;
  private static final String DEFAULT_PRIVATE_KEY_BEGIN = "-----BEGIN RSA PRIVATE KEY-----";
  private static final String DEFAULT_PRIVATE_KEY_END = "-----END RSA PRIVATE KEY-----";
  private static final String DEFAULT_PUBLIC_KEY_BEGIN = "ssh-rsa";

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
  class GetSshKeyPair {
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

  private static SshKeyPair createRandomGithubSshKey()
      throws NoSuchAlgorithmException, IOException {
    var randomExternalUserEmail =
        RandomStringUtils.random(5, /*letters=*/ true, /*numbers=*/ true) + "@gmail.com";
    var keyPair = generateRSAKeyPair();
    return new SshKeyPair.Builder()
        .type(DEFAULT_KEY_TYPE)
        .privateKey(encodeRSAPrivateKey((RSAPrivateKey) keyPair.getPrivate()))
        .publicKey(encodeRSAPublicKey((RSAPublicKey) keyPair.getPublic(), randomExternalUserEmail))
        .userId(UUID.randomUUID().toString())
        .externalUserEmail(
            RandomStringUtils.random(5, /*letters=*/ true, /*numbers=*/ true) + "@gmail.com")
        .build();
  }

  private static String encodeRSAPrivateKey(RSAPrivateKey privateKey) {
    System.out.println(
        "DEFAULT_PRIVATE_KEY_BEGIN\n"
            + "        + \"\\n\"\n"
            + "        + new String(Base64.encodeBase64(privateKey.getEncoded()))\n"
            + "        + \"\\n\"\n"
            + "        + DEFAULT_PRIVATE_KEY_END");
    return DEFAULT_PRIVATE_KEY_BEGIN
        + "\n"
        + new String(Base64.encodeBase64(privateKey.getEncoded()))
        + "\n"
        + DEFAULT_PRIVATE_KEY_END;
  }

  private static String encodeRSAPublicKey(RSAPublicKey rsaPublicKey, String user) throws IOException {
    ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(byteOs);
    dos.writeInt("ssh-rsa".getBytes().length);
    dos.write("ssh-rsa".getBytes());
    dos.writeInt(rsaPublicKey.getPublicExponent().toByteArray().length);
    dos.write(rsaPublicKey.getPublicExponent().toByteArray());
    dos.writeInt(rsaPublicKey.getModulus().toByteArray().length);
    dos.write(rsaPublicKey.getModulus().toByteArray());
    var publicKeyEncoded = new String(Base64.encodeBase64(byteOs.toByteArray()));
    return DEFAULT_PUBLIC_KEY_BEGIN + " " + publicKeyEncoded + " " + user;
  }

  private static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private void verifySshKeyPair(SshKeyPair expectedSshKey, SshKeyPair actualSshKey) {
    assertEquals(expectedSshKey.withId(actualSshKey.getId()), actualSshKey);
  }
}
