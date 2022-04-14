package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ExternalCredsConfigInterface.KmsConfiguration;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import java.io.IOException;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.springframework.stereotype.Component;

/** Utils for KMS symmetric encryption and decryption. */
@Component
@Slf4j
public class KmsEncryptDecryptHelper {
  private final ExternalCredsConfig config;
  private @MonotonicNonNull KeyManagementServiceClient client;

  KmsEncryptDecryptHelper(ExternalCredsConfig config) {
    this.config = config;
  }

  /** Encrypt with KMS symmetric key. */
  public String encryptSymmetric(String plainText) {
    if (config.getKmsConfiguration().isEmpty()) {
      log.info("KMS encryption for ssh private keys is disabled");
      return plainText;
    }
    var keyVersionName = getCryptoKeyName();
    EncryptResponse response = client.encrypt(keyVersionName, ByteString.copyFromUtf8(plainText));
    return response.getCiphertext().toStringUtf8();
  }

  /** Decrypt with KMS symmetric key. */
  public String decryptSymmetric(String cypheredText) {
    if (config.getKmsConfiguration().isEmpty()) {
      log.info("KMS encryption for ssh private keys is disabled");
      return cypheredText;
    }
    var keyName = getCryptoKeyName();
    DecryptResponse response = client.decrypt(keyName, ByteString.copyFromUtf8(cypheredText));
    return response.getPlaintext().toStringUtf8();
  }

  @PostConstruct
  private void instantiateKeyManagementServiceClient() {
    config
        .getKmsConfiguration()
        .ifPresent(
            x -> {
              try {
                client = KeyManagementServiceClient.create();
              } catch (IOException e) {
                throw new ExternalCredsException("Fail to get KMS client for encryption.", e);
              }
            });
  }

  @PreDestroy
  private void closeKeyManagementServiceClient() {
    if (client != null) client.close();
  }

  private CryptoKeyName getCryptoKeyName() {
    Preconditions.checkState(config.getKmsConfiguration().isPresent());
    KmsConfiguration kmsConfiguration = config.getKmsConfiguration().get();
    return CryptoKeyName.of(
        kmsConfiguration.getServiceGoogleProject(),
        kmsConfiguration.getKeyRingLocation(),
        kmsConfiguration.getKeyRingId(),
        kmsConfiguration.getKeyId());
  }
}
