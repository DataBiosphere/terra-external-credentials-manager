package bio.terra.externalcreds;

import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.models.ImmutableGA4GHPassport;
import bio.terra.externalcreds.models.ImmutableGA4GHVisa;
import bio.terra.externalcreds.models.ImmutableLinkedAccount;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;

public class TestUtils {

  public static Timestamp getRandomTimestamp() {
    return new Timestamp(System.currentTimeMillis());
  }

  public static ImmutableLinkedAccount createRandomLinkedAccount() {
    return ImmutableLinkedAccount.builder()
        .expires(getRandomTimestamp())
        .providerId(UUID.randomUUID().toString())
        .refreshToken(UUID.randomUUID().toString())
        .userId(UUID.randomUUID().toString())
        .externalUserId(UUID.randomUUID().toString())
        .build();
  }

  public static ImmutableGA4GHPassport createRandomPassport() {
    return ImmutableGA4GHPassport.builder()
        .jwt(UUID.randomUUID().toString())
        .expires(getRandomTimestamp())
        .build();
  }

  public static ImmutableGA4GHVisa createRandomVisa() {
    return ImmutableGA4GHVisa.builder()
        .visaType(UUID.randomUUID().toString())
        .tokenType(TokenTypeEnum.access_token)
        .expires(getRandomTimestamp())
        .issuer(UUID.randomUUID().toString())
        .jwt(UUID.randomUUID().toString())
        .build();
  }

  public static ProviderProperties createRandomProvider() {
    try {
      return ProviderProperties.create()
          .setClientId(UUID.randomUUID().toString())
          .setClientSecret(UUID.randomUUID().toString())
          .setIssuer(UUID.randomUUID().toString())
          .setLinkLifespan(Duration.ofDays(SecureRandom.getInstanceStrong().nextInt(10)))
          .setRevokeEndpoint("http://does/not/exist");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
