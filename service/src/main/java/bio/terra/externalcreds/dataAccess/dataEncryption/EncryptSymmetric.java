package bio.terra.externalcreds.dataAccess.dataEncryption;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import java.io.IOException;

public class EncryptSymmetric {

  public static byte[] encryptSymmetric(
      String projectId, String locationId, String keyRingId, String keyId, byte[] plaintext)
      throws IOException {
    try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
      CryptoKeyName keyVersionName = CryptoKeyName.of(projectId, locationId, keyRingId, keyId);

      EncryptResponse response = client.encrypt(keyVersionName, ByteString.copyFrom(plaintext));
      client.close();
      return response.getCiphertext().toByteArray();
    }
  }
}
