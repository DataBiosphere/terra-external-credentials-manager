package bio.terra.externalcreds.services;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.KmsConfiguration;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Utils for KMS symmetric encryption and decryption. */
@Component
@Slf4j
public class KmsEncryptDecryptHelper {
  private final ExternalCredsConfig config;
  private @Nullable KeyManagementServiceClient client;

  KmsEncryptDecryptHelper(ExternalCredsConfig config) {
    this.config = config;
  }

  @PostConstruct
  private void instantiateKeyManagementServiceClient() {
    Optional.ofNullable(config.getKmsConfiguration())
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
    if (client != null) {
      client.close();
    }
  }

  /** Encrypt with KMS symmetric key. */
  @Traced
  public byte[] encryptSymmetric(byte[] plainText) {
    if (client == null || config.getKmsConfiguration() == null) {
      log.info("KMS encryption for ssh private keys is disabled");
      return plainText;
    }
    var keyVersionName = getCryptoKeyName();
    EncryptResponse response = client.encrypt(keyVersionName, ByteString.copyFrom(plainText));
    return response.getCiphertext().toByteArray();
  }

  /** Decrypt with KMS symmetric key. */
  @Traced
  public byte[] decryptSymmetric(byte[] cypheredText) {
    if (client == null || config.getKmsConfiguration() == null) {
      log.info("KMS encryption for ssh private keys is disabled");
      return cypheredText;
    }
    var keyName = getCryptoKeyName();
    DecryptResponse response = client.decrypt(keyName, ByteString.copyFrom(cypheredText));
    return response.getPlaintext().toByteArray();
  }

  private CryptoKeyName getCryptoKeyName() {
    KmsConfiguration kmsConfiguration = Preconditions.checkNotNull(config.getKmsConfiguration());
    return CryptoKeyName.of(
        kmsConfiguration.getServiceGoogleProject(),
        kmsConfiguration.getKeyRingLocation(),
        kmsConfiguration.getKeyRingId(),
        kmsConfiguration.getKeyId());
  }
}
