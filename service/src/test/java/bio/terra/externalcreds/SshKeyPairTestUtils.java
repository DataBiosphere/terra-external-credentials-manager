package bio.terra.externalcreds;

import bio.terra.externalcreds.config.KmsConfiguration;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class SshKeyPairTestUtils {
  private static final String DEFAULT_PRIVATE_KEY_BEGIN = "-----BEGIN SSH PRIVATE KEY-----";
  private static final String DEFAULT_PRIVATE_KEY_END = "-----END SSH PRIVATE KEY-----";
  private static final String DEFAULT_PUBLIC_KEY_BEGIN = "ssh-rsa";

  public static SshKeyPairInternal createRandomGithubSshKey()
      throws NoSuchAlgorithmException, IOException {
    var randomExternalUserEmail =
        RandomStringUtils.random(5, /*letters=*/ true, /*numbers=*/ true) + "@gmail.com";
    KeyPair keyPair = generateRSAKeyPair();
    return new SshKeyPairInternal.Builder()
        .type(SshKeyPairType.GITHUB)
        .privateKey(
            encodeRSAPrivateKey((RSAPrivateKey) keyPair.getPrivate())
                .getBytes(StandardCharsets.UTF_8))
        .publicKey(encodeRSAPublicKey((RSAPublicKey) keyPair.getPublic(), randomExternalUserEmail))
        .userId(UUID.randomUUID().toString())
        .externalUserEmail(
            RandomStringUtils.random(5, /*letters=*/ true, /*numbers=*/ true) + "@gmail.com")
        .build();
  }

  /** Gets a pair of private - public RSA key. */
  public static Pair<String, String> getRSAEncodedKeyPair(String externalUser)
      throws NoSuchAlgorithmException, IOException {
    KeyPair keyPair = generateRSAKeyPair();
    return Pair.of(
        encodeRSAPrivateKey((RSAPrivateKey) keyPair.getPrivate()),
        encodeRSAPublicKey((RSAPublicKey) keyPair.getPublic(), externalUser));
  }

  private static String encodeRSAPrivateKey(RSAPrivateKey privateKey) {
    return DEFAULT_PRIVATE_KEY_BEGIN
        + "\n"
        + new String(Base64.encodeBase64(privateKey.getEncoded()))
        + "\n"
        + DEFAULT_PRIVATE_KEY_END;
  }

  private static String encodeRSAPublicKey(RSAPublicKey rsaPublicKey, String user)
      throws IOException {
    var byteOs = new ByteArrayOutputStream();
    var dos = new DataOutputStream(byteOs);
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

  public static KmsConfiguration getDefaultFakeKmsConfiguration() {
    return KmsConfiguration.create()
        .setKeyId("key-id")
        .setKeyRingId("key-ring")
        .setServiceGoogleProject("google-project")
        .setKeyRingLocation("us-central1")
        .setSshKeyPairRefreshDuration(Duration.ZERO);
  }

  public static KmsConfiguration getFakeKmsConfiguration(Duration sshKeyPairRefreshDuration) {
    return getDefaultFakeKmsConfiguration().setSshKeyPairRefreshDuration(sshKeyPairRefreshDuration);
  }

  /**
   *  Delete all the row in the ssh_key_pair data table. This is so that if there are other
   *  un-encrypted or expired key in the database left over from other tests, they create noise
   *  to the test as we will attempt to encrypt them as well.
   */
  public static void cleanUp(NamedParameterJdbcTemplate jdbcTemplate) {
    // Delete all the row in the ssh_key_pair data table.
    var deleteAll = "DELETE FROM ssh_key_pair";
    jdbcTemplate.update(deleteAll, new MapSqlParameterSource());
  }
}
