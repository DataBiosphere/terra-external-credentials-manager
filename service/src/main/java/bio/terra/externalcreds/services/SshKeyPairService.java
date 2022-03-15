package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
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
import java.util.Optional;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SshKeyPairService {

  private static final String DEFAULT_PRIVATE_KEY_BEGIN = "-----BEGIN RSA PRIVATE KEY-----";
  private static final String DEFAULT_PRIVATE_KEY_END = "-----END RSA PRIVATE KEY-----";
  private static final String DEFAULT_PUBLIC_KEY_BEGIN = "ssh-rsa";

  private final SshKeyPairDAO sshKeyPairDAO;

  public SshKeyPairService(SshKeyPairDAO sshKeyPairDAO) {
    this.sshKeyPairDAO = sshKeyPairDAO;
  }

  @ReadTransaction
  public Optional<SshKeyPair> getSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO.getSshKeyPair(userId, type);
  }

  @WriteTransaction
  public SshKeyPair putSshKeyPair(
      String userId,
      SshKeyPairType type,
      String privateKey,
      String publicKey,
      String externalUserEmail) {
    return sshKeyPairDAO.upsertSshKeyPair(
        new SshKeyPair.Builder()
            .privateKey(privateKey)
            .publicKey(publicKey)
            .externalUserEmail(externalUserEmail)
            .userId(userId)
            .type(type)
            .build());
  }

  @WriteTransaction
  public boolean deleteSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO.deleteSshKeyPair(userId, type);
  }

  @WriteTransaction
  public SshKeyPair generateSshKeyPair(
      String userId, String externalUserEmail, SshKeyPairType type) {
    try {
      KeyPair rsaKeyPair = generateRSAKeyPair();
      return sshKeyPairDAO.upsertSshKeyPair(
          new SshKeyPair.Builder()
              .privateKey(encodeRSAPrivateKey((RSAPrivateKey) rsaKeyPair.getPrivate()))
              .publicKey(
                  encodeRSAPublicKey((RSAPublicKey) rsaKeyPair.getPublic(), externalUserEmail))
              .externalUserEmail(externalUserEmail)
              .type(type)
              .userId(userId)
              .build());
    } catch (NoSuchAlgorithmException | IOException e) {
      throw new ExternalCredsException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
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
}
