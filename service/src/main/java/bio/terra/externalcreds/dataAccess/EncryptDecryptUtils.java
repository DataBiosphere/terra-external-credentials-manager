package bio.terra.externalcreds.dataAccess;

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
      String projectId, String locationId, String keyRingId, String keyId, String plainText)
      throws IOException {
    try (var client = KeyManagementServiceClient.create()) {
      var keyVersionName = CryptoKeyName.of(projectId, locationId, keyRingId, keyId);
      EncryptResponse response = client.encrypt(keyVersionName, ByteString.copyFromUtf8(plainText));
      client.close();
      return response.getCiphertext().toStringUtf8();
    }
  }

  /** Decrypt with KMS symmetric key. */
  public static String decryptSymmetric(
      String projectId, String locationId, String keyRingId, String keyId, String cypheredText)
      throws IOException {
    try (var client = KeyManagementServiceClient.create()) {
      var keyName = CryptoKeyName.of(projectId, locationId, keyRingId, keyId);
      DecryptResponse response = client.decrypt(keyName, ByteString.copyFromUtf8(cypheredText));
      client.close();
      return response.getPlaintext().toStringUtf8();
    }
  }
}
