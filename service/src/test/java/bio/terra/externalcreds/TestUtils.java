package bio.terra.externalcreds;

import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.SshKeyPair;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class TestUtils {
  private static final String DEFAULT_PRIVATE_KEY_BEGIN = "-----BEGIN SSH PRIVATE KEY-----";
  private static final String DEFAULT_PRIVATE_KEY_END = "-----END SSH PRIVATE KEY-----";
  private static final String DEFAULT_PUBLIC_KEY_BEGIN = "ssh-rsa";

  public static Timestamp getRandomTimestamp() {
    return new Timestamp(System.currentTimeMillis());
  }

  public static LinkedAccount createRandomLinkedAccount() {
    return new LinkedAccount.Builder()
        .expires(getRandomTimestamp())
        .providerName(UUID.randomUUID().toString())
        .refreshToken(UUID.randomUUID().toString())
        .userId(UUID.randomUUID().toString())
        .externalUserId(UUID.randomUUID().toString())
        .isAuthenticated(true)
        .build();
  }

  public static GA4GHPassport createRandomPassport() {
    return new GA4GHPassport.Builder()
        .jwt(UUID.randomUUID().toString())
        .expires(getRandomTimestamp())
        .jwtId(UUID.randomUUID().toString())
        .build();
  }

  public static GA4GHVisa createRandomVisa() {
    return new GA4GHVisa.Builder()
        .visaType(UUID.randomUUID().toString())
        .tokenType(TokenTypeEnum.access_token)
        .expires(getRandomTimestamp())
        .issuer(UUID.randomUUID().toString())
        .jwt(UUID.randomUUID().toString())
        .lastValidated(getRandomTimestamp())
        .build();
  }

  public static ProviderProperties createRandomProvider() {
    try {
      return ProviderProperties.create()
          .setClientId(UUID.randomUUID().toString())
          .setClientSecret(UUID.randomUUID().toString())
          .setIssuer("http://does/not/exist")
          .setLinkLifespan(Duration.ofDays(SecureRandom.getInstanceStrong().nextInt(10)))
          .setRevokeEndpoint("http://does/not/exist");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static VisaVerificationDetails createRandomVisaVerificationDetails() {
    return new VisaVerificationDetails.Builder()
        .visaId(21)
        .linkedAccountId(42)
        .providerName(UUID.randomUUID().toString())
        .visaJwt(UUID.randomUUID().toString())
        .build();
  }

  public static SshKeyPair createRandomGithubSshKey() throws NoSuchAlgorithmException, IOException {
    var randomExternalUserEmail =
        RandomStringUtils.random(5, /*letters=*/ true, /*numbers=*/ true) + "@gmail.com";
    KeyPair keyPair = generateRSAKeyPair();
    return new SshKeyPair.Builder()
        .type(SshKeyPairType.GITHUB)
        .privateKey(encodeRSAPrivateKey((RSAPrivateKey) keyPair.getPrivate()))
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

  public static Throwable getRootCause(Throwable throwable) {
    // https://www.baeldung.com/java-exception-root-cause
    Objects.requireNonNull(throwable);
    Throwable rootCause = throwable;
    while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
      rootCause = rootCause.getCause();
    }
    return rootCause;
  }
}
