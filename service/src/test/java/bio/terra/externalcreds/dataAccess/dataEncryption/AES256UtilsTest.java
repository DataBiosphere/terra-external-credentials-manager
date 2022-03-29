package bio.terra.externalcreds.dataAccess.dataEncryption;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.BaseTest;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.junit.jupiter.api.Test;

public class AES256UtilsTest extends BaseTest {

  @Test
  void encryptAndDecrypt()
      throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchPaddingException,
          IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
    String input = "shhh, this is a secret";
    SecretKey key = AES256Utils.generateAES256Key();
    IvParameterSpec ivParameterSpec = AES256Utils.generateIv();
    String ciphertext = AES256Utils.encrypt(input, key, ivParameterSpec);
    String plainText = AES256Utils.decrypt(ciphertext, key.getEncoded(), ivParameterSpec.getIV());
    assertEquals(input, plainText);
  }
}
