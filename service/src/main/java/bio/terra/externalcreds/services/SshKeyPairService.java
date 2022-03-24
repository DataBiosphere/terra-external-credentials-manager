package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
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
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SshKeyPairService {

  private static final String DEFAULT_PUBLIC_KEY_BEGIN = "ssh-rsa";

  private final SshKeyPairDAO sshKeyPairDAO;

  public SshKeyPairService(SshKeyPairDAO sshKeyPairDAO) {
    this.sshKeyPairDAO = sshKeyPairDAO;
  }

  @ReadTransaction
  public SshKeyPairInternal getSshKeyPair(String userId, SshKeyPairType type) {
    return sshKeyPairDAO
        .getSshKeyPair(userId, type)
        .orElseThrow(() -> new NotFoundException("Ssh Key is not found"));
  }

  @WriteTransaction
  public void deleteSshKeyPair(String userId, SshKeyPairType type) {
    var existed = sshKeyPairDAO.deleteSshKeyPairIfExists(userId, type);
    if (!existed) {
      throw new NotFoundException("Delete nothing because Ssh key pair is not found");
    }
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
      throw new ExternalCredsException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private static String encodeRSAPrivateKey(RSAPrivateKey privateKey) {
    return new String(Base64.encodeBase64(privateKey.getEncoded()), StandardCharsets.UTF_8);
  }

  private static String encodeRSAPublicKey(RSAPublicKey rsaPublicKey, String user)
      throws IOException {
    ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(byteOs);
    dos.writeInt("ssh-rsa".getBytes(StandardCharsets.UTF_8).length);
    dos.write("ssh-rsa".getBytes(StandardCharsets.UTF_8));
    dos.writeInt(rsaPublicKey.getPublicExponent().toByteArray().length);
    dos.write(rsaPublicKey.getPublicExponent().toByteArray());
    dos.writeInt(rsaPublicKey.getModulus().toByteArray().length);
    dos.write(rsaPublicKey.getModulus().toByteArray());
    var publicKeyEncoded =
        new String(Base64.encodeBase64(byteOs.toByteArray()), StandardCharsets.UTF_8);
    return DEFAULT_PUBLIC_KEY_BEGIN + " " + publicKeyEncoded + " " + user;
  }

  private static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
