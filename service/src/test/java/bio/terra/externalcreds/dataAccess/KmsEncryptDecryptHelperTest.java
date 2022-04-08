package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ExternalCredsConfigInterface.KmsConfiguration;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class KmsEncryptDecryptHelperTest extends BaseTest {

  @Autowired KmsEncryptDecryptHelper kmsEncryptDecryptHelper;
  @MockBean ExternalCredsConfig config;
  @MockBean KeyManagementServiceClient keyManagementServiceClient;

  @Test
  void tryToEncryptWithoutKmsConfig() {
    try (var mockClient = Mockito.mockStatic(KeyManagementServiceClient.class)) {
      mockClient.when(KeyManagementServiceClient::create).thenReturn(keyManagementServiceClient);
      assertThrows(
          UnsupportedOperationException.class,
          () -> kmsEncryptDecryptHelper.encryptSymmetric("secret"));
    }
  }

  @Test
  void tryToDecryptWithoutKmsConfig() {
    try (var mockClient = Mockito.mockStatic(KeyManagementServiceClient.class)) {
      mockClient.when(KeyManagementServiceClient::create).thenReturn(keyManagementServiceClient);
      assertThrows(
          UnsupportedOperationException.class,
          () -> kmsEncryptDecryptHelper.decryptSymmetric("aserq2ji3"));
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

  @Test
  void failToGetKmsClient() {
    setUpKmsConfigMock();
    try (var mockClient = Mockito.mockStatic(KeyManagementServiceClient.class)) {
      mockClient.when(KeyManagementServiceClient::create).thenThrow(new IOException());

      assertThrows(
          ExternalCredsException.class,
          () -> kmsEncryptDecryptHelper.encryptSymmetric("plain text"));
      assertThrows(
          ExternalCredsException.class, () -> kmsEncryptDecryptHelper.decryptSymmetric("ji3m1o32"));
    }
  }

  private void setUpKmsConfigMock() {
    when(config.getKmsConfiguration())
        .thenReturn(
            java.util.Optional.of(
                new KmsConfiguration() {
                  @Override
                  public String getServiceGoogleProject() {
                    return "project";
                  }

                  @Override
                  public String getKeyRingId() {
                    return "key-ring";
                  }

                  @Override
                  public String getKeyId() {
                    return "key-id";
                  }

                  @Override
                  public String getKeyRingLocation() {
                    return "us-central1";
                  }

                  @Override
                  public Duration getKeyRotationIntervalDays() {
                    return Duration.ZERO;
                  }

                  @Override
                  public int getInitialDelayDays() {
                    return 0;
                  }

                  @Override
                  public int getReEncryptionDays() {
                    return 0;
                  }
                }));
  }
}
