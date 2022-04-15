package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.KmsConfiguration;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class KmsEncryptDecryptHelperTest extends BaseTest {

  @Autowired KmsEncryptDecryptHelper kmsEncryptDecryptHelper;
  @MockBean KmsConfiguration kmsConfiguration;
  @MockBean KeyManagementServiceClient keyManagementServiceClient;

  @Test
  void tryToEncryptWithoutKmsConfig() {
    try (var mockClient = Mockito.mockStatic(KeyManagementServiceClient.class)) {
      mockClient.when(KeyManagementServiceClient::create).thenReturn(keyManagementServiceClient);
      var secret = "secret";
      assertEquals(secret, kmsEncryptDecryptHelper.encryptSymmetric(secret));
    }
  }

  @Test
  void tryToDecryptWithoutKmsConfig() {
    try (var mockClient = Mockito.mockStatic(KeyManagementServiceClient.class)) {
      mockClient.when(KeyManagementServiceClient::create).thenReturn(keyManagementServiceClient);
      var cypheredText = "secret";
      assertEquals(cypheredText, kmsEncryptDecryptHelper.decryptSymmetric(cypheredText));
    }
  }

  @Test
  void encrypt() {
    setUpKmsConfigMock();
    var plainText = "secret";
    var cypheredText = "jeij1lm3";
    when(keyManagementServiceClient.encrypt(
            any(CryptoKeyName.class), eq(ByteString.copyFromUtf8(plainText))))
        .thenReturn(
            EncryptResponse.newBuilder()
                .setCiphertext(ByteString.copyFromUtf8(cypheredText))
                .build());
    try (var mockClient = Mockito.mockStatic(KeyManagementServiceClient.class)) {
      mockClient.when(KeyManagementServiceClient::create).thenReturn(keyManagementServiceClient);

      assertEquals(cypheredText, kmsEncryptDecryptHelper.encryptSymmetric(plainText));
    }
  }

  @Test
  void decrypt() {
    setUpKmsConfigMock();
    var plainText = "secret";
    var cypheredText = "jeij1lm3";
    when(keyManagementServiceClient.decrypt(
            any(CryptoKeyName.class), eq(ByteString.copyFromUtf8(cypheredText))))
        .thenReturn(
            DecryptResponse.newBuilder().setPlaintext(ByteString.copyFromUtf8(plainText)).build());
    try (var mockClient = Mockito.mockStatic(KeyManagementServiceClient.class)) {
      mockClient.when(KeyManagementServiceClient::create).thenReturn(keyManagementServiceClient);

      assertEquals(plainText, kmsEncryptDecryptHelper.decryptSymmetric(cypheredText));
    }
  }

  private void setUpKmsConfigMock() {
    when(kmsConfiguration.getKeyId()).thenReturn("keyId");
    when(kmsConfiguration.getKeyRingId()).thenReturn("keyRingId");
    when(kmsConfiguration.getKeyRingLocation()).thenReturn("us-central1");
    when(kmsConfiguration.getServiceGoogleProject()).thenReturn("google-project");
  }
}
