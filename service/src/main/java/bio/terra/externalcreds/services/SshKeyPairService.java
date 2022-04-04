package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.stereotype.Service;

@Service
public class SshKeyPairService {

  private static final String DEFAULT_PUBLIC_KEY_BEGIN = "ssh-rsa";

  private final SshKeyPairDAO sshKeyPairDAO;

  public SshKeyPairService(SshKeyPairDAO sshKeyPairDAO) {
    this.sshKeyPairDAO = sshKeyPairDAO;
  }

  @ReadTransaction
  public Optional<SshKeyPairInternal> getSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO.getSshKeyPair(userId, type);
  }

  @WriteTransaction
  public boolean deleteSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO.deleteSshKeyPair(userId, type);
  }

  @WriteTransaction
  public SshKeyPairInternal putSshKeyPair(
      String userId, SshKeyPairType type, SshKeyPair sshKeyPair) {
    return sshKeyPairDAO.upsertSshKeyPair(
          new SshKeyPairInternal.Builder()
              .privateKey(sshKeyPair.getPrivateKey())
              .publicKey(sshKeyPair.getPublicKey())
              .externalUserEmail(sshKeyPair.getExternalUserEmail())
              .userId(userId)
              .type(type)
              .build());
  }

  @WriteTransaction
  public SshKeyPairInternal generateSshKeyPair(
      String userId, String externalUserEmail, SshKeyPairType type) {
    try {
      KeyPair rsaKeyPair = generateRSAKeyPair();
      return sshKeyPairDAO.upsertSshKeyPair(
          new SshKeyPairInternal.Builder()
              .privateKey(encodeRSAPrivateKey((RSAPrivateKey) rsaKeyPair.getPrivate()))
              .publicKey(
                  encodeRSAPublicKey((RSAPublicKey) rsaKeyPair.getPublic(), externalUserEmail))
              .externalUserEmail(externalUserEmail)
              .type(type)
              .userId(userId)
              .build());
    } catch (NoSuchAlgorithmException | IOException e) {
      throw new ExternalCredsException(e);
    }
  }

  /** Encode RSA private key to PEM format. */
  private static String encodeRSAPrivateKey(RSAPrivateKey privateKey) throws IOException {
    var pemObject = new PemObject("RSA PRIVATE KEY", privateKey.getEncoded());
    var byteStream = new ByteArrayOutputStream();
    var pemWriter = new PemWriter(new OutputStreamWriter(byteStream, StandardCharsets.UTF_8));
    pemWriter.writeObject(pemObject);
    pemWriter.close();
    return byteStream.toString(StandardCharsets.UTF_8);
  }

  /**
   * Write public key in the OpenSSL format.
   *
   * <p>GitHub has restriction on public key format so we append ssh-rsa in front and construct the
   * ssh key to conform to the OpenSSL format.
   */
  private static String encodeRSAPublicKey(RSAPublicKey rsaPublicKey, String user)
      throws IOException {
    var byteOs = new ByteArrayOutputStream();
    var dos = new DataOutputStream(byteOs);
    dos.writeInt(DEFAULT_PUBLIC_KEY_BEGIN.getBytes(StandardCharsets.UTF_8).length);
    dos.write(DEFAULT_PUBLIC_KEY_BEGIN.getBytes(StandardCharsets.UTF_8));
    dos.writeInt(rsaPublicKey.getPublicExponent().toByteArray().length);
    dos.write(rsaPublicKey.getPublicExponent().toByteArray());
    dos.writeInt(rsaPublicKey.getModulus().toByteArray().length);
    dos.write(rsaPublicKey.getModulus().toByteArray());
    var publicKeyEncoded =
        new String(Base64.encodeBase64(byteOs.toByteArray()), StandardCharsets.UTF_8);
    dos.close();
    return DEFAULT_PUBLIC_KEY_BEGIN + " " + publicKeyEncoded + " " + user;
  }

  private static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(4096);
    return generator.generateKeyPair();
  }
}
