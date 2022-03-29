package bio.terra.externalcreds.dataAccess.dataEncryption;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import java.io.IOException;

public class DecryptSymmetric {

  public static byte[] decryptSymmetric(
      String projectId, String locationId, String keyRingId, String keyId, byte[] ciphertext)
      throws IOException {
    try (var client = KeyManagementServiceClient.create()) {
      var keyName = CryptoKeyName.of(projectId, locationId, keyRingId, keyId);

      DecryptResponse response = client.decrypt(keyName, ByteString.copyFrom(ciphertext));
      client.close();
      return response.getPlaintext().toByteArray();
    }
  }
}
