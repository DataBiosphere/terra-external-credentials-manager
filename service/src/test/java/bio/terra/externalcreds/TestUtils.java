package bio.terra.externalcreds;

import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;

public class TestUtils {

  public static Timestamp getRandomTimestamp() {
    return new Timestamp(System.currentTimeMillis());
  }

  public static LinkedAccount createRandomLinkedAccount() {
    return new LinkedAccount.Builder()
        .expires(getRandomTimestamp())
        .providerName(UUID.randomUUID().toString())
        .refreshToken(UUID.randomUUID().toString())
        .userId(UUID.randomUUID().toString())
        .externalUserId(UUID.randomUUID().toString())
        .isAuthenticated(true)
        .build();
  }

  public static GA4GHPassport createRandomPassport() {
    return new GA4GHPassport.Builder()
        .jwt(UUID.randomUUID().toString())
        .expires(getRandomTimestamp())
        .build();
  }

  public static GA4GHVisa createRandomVisa() {
    return new GA4GHVisa.Builder()
        .visaType(UUID.randomUUID().toString())
        .tokenType(TokenTypeEnum.access_token)
        .expires(getRandomTimestamp())
        .issuer(UUID.randomUUID().toString())
        .jwt(UUID.randomUUID().toString())
        .lastValidated(getRandomTimestamp())
        .build();
  }

  public static ProviderProperties createRandomProvider() {
    try {
      return ProviderProperties.create()
          .setClientId(UUID.randomUUID().toString())
          .setClientSecret(UUID.randomUUID().toString())
          .setIssuer("http://does/not/exist")
          .setLinkLifespan(Duration.ofDays(SecureRandom.getInstanceStrong().nextInt(10)))
          .setRevokeEndpoint("http://does/not/exist");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static VisaVerificationDetails createRandomVisaVerificationDetails() {
      return new VisaVerificationDetails.Builder()
              .providerName(UUID.randomUUID().toString())
              .visaJwt(UUID.randomUUID().toString())
              .build();
  }
}
