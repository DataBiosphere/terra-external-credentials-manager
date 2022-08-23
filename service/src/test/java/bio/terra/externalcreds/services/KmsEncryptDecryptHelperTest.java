package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@Tag("unit")
class KmsEncryptDecryptHelperTest extends BaseTest {

  @Autowired KmsEncryptDecryptHelper encryptDecryptHelper;

  @Test
  void encryptAndDecrypt() {
    var secretText = "this is a secret";

    var encryptedText =
        encryptDecryptHelper.encryptSymmetric(secretText.getBytes(StandardCharsets.UTF_8));
    var decryptedText = encryptDecryptHelper.decryptSymmetric(encryptedText);

    assertNotEquals(secretText, new String(encryptedText, StandardCharsets.UTF_8));
    assertEquals(secretText, new String(decryptedText, StandardCharsets.UTF_8));
  }

  @Nested
  class KmsDisabled {
    @Autowired KmsEncryptDecryptHelper encryptDecryptHelper;
    @MockBean ExternalCredsConfig config;

    @Test
    void encryptAndDecryptDoNothing() {
      when(config.getKmsConfiguration()).thenReturn(null);
      var secretText = "this is a secret";

      var encryptedText =
          encryptDecryptHelper.encryptSymmetric(secretText.getBytes(StandardCharsets.UTF_8));
      var decryptedText = encryptDecryptHelper.decryptSymmetric(encryptedText);

      assertEquals(secretText, new String(encryptedText, StandardCharsets.UTF_8));
      assertEquals(secretText, new String(decryptedText, StandardCharsets.UTF_8));
    }
  }
}
