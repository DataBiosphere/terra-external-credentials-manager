package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ExternalCredsConfigInterface.KmsConfiguration;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import java.io.IOException;
import org.springframework.stereotype.Component;

/** Utils for KMS symmetric encryption and decryption. */
@Component
public record KmsEncryptDecryptHelper(ExternalCredsConfig config) {

  /** Encrypt with KMS symmetric key. */
  public String encryptSymmetric(String plainText) {
    try (var client = KeyManagementServiceClient.create()) {
      var keyVersionName = getCryptoKeyName();
      EncryptResponse response = client.encrypt(keyVersionName, ByteString.copyFromUtf8(plainText));
      return response.getCiphertext().toStringUtf8();
    } catch (IOException e) {
      throw new ExternalCredsException("Fail to get KMS client for encryption.");
    }
  }

  /** Decrypt with KMS symmetric key. */
  public String decryptSymmetric(String cypheredText) {
    try (var client = KeyManagementServiceClient.create()) {
      var keyName = getCryptoKeyName();
      DecryptResponse response = client.decrypt(keyName, ByteString.copyFromUtf8(cypheredText));
      return response.getPlaintext().toStringUtf8();
    } catch (IOException e) {
      throw new ExternalCredsException("Fail to get KMS client for decryption.");
    }
  }

  private CryptoKeyName getCryptoKeyName() {
    KmsConfiguration kmsConfiguration = config.getKmsConfiguration().get();
    return CryptoKeyName.of(
        kmsConfiguration.getServiceGoogleProject(),
        kmsConfiguration.getKeyRingLocation(),
        kmsConfiguration.getKeyRingId(),
        kmsConfiguration.getKeyId());
  }
}
