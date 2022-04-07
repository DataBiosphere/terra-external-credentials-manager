package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.ExternalCredsException;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import java.io.IOException;

/** Utils for KMS symmetric encryption and decryption. */
public class EncryptDecryptUtils {

  private EncryptDecryptUtils() {}

  /** Encrypt with KMS symmetric key. */
  public static String encryptSymmetric(
      String projectId, String locationId, String keyRingId, String keyId, String plainText) {
    try (var client = KeyManagementServiceClient.create()) {
      var keyVersionName = CryptoKeyName.of(projectId, locationId, keyRingId, keyId);
      EncryptResponse response = client.encrypt(keyVersionName, ByteString.copyFromUtf8(plainText));
      return response.getCiphertext().toStringUtf8();
    } catch (IOException e) {
      throw new ExternalCredsException("Fail to get KMS client for encryption.");
    }
  }

  /** Decrypt with KMS symmetric key. */
  public static String decryptSymmetric(
      String projectId, String locationId, String keyRingId, String keyId, String cypheredText) {
    try (var client = KeyManagementServiceClient.create()) {
      var keyName = CryptoKeyName.of(projectId, locationId, keyRingId, keyId);
      DecryptResponse response = client.decrypt(keyName, ByteString.copyFromUtf8(cypheredText));
      return response.getPlaintext().toStringUtf8();
    } catch (IOException e) {
      throw new ExternalCredsException("Fail to get KMS client for decryption.");
    }
  }
}
