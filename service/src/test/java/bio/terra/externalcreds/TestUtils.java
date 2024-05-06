package bio.terra.externalcreds;

import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.AccessTokenCacheEntry;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

public class TestUtils {

  public static Timestamp getFutureTimestamp() {
    return new Timestamp(System.currentTimeMillis() + 60 * 1000);
  }

  public static LinkedAccount createRandomLinkedAccount() {
    return createRandomLinkedAccount(Provider.GITHUB);
  }

  public static LinkedAccount createRandomPassportLinkedAccount() {
    return createRandomLinkedAccount(Provider.RAS);
  }

  public static LinkedAccount createRandomLinkedAccount(Provider provider) {
    return new LinkedAccount.Builder()
        .expires(getFutureTimestamp())
        .provider(provider)
        .refreshToken(UUID.randomUUID().toString())
        .userId(UUID.randomUUID().toString())
        .externalUserId(UUID.randomUUID().toString())
        .isAuthenticated(true)
        .build();
  }

  public static GA4GHPassport createRandomPassport() {
    return new GA4GHPassport.Builder()
        .jwt(UUID.randomUUID().toString())
        .expires(getFutureTimestamp())
        .jwtId(UUID.randomUUID().toString())
        .build();
  }

  public static GA4GHVisa createRandomVisa() {
    return new GA4GHVisa.Builder()
        .visaType(UUID.randomUUID().toString())
        .tokenType(TokenTypeEnum.access_token)
        .expires(getFutureTimestamp())
        .issuer(UUID.randomUUID().toString())
        .jwt(UUID.randomUUID().toString())
        .lastValidated(getFutureTimestamp())
        .build();
  }

  public static FenceAccountKey createRandomFenceAccountKey() {
    return new FenceAccountKey.Builder()
        .linkedAccountId(1)
        .keyJson("{\"key\": \"value\", \"private_key_id\": \"12345\"}")
        .expiresAt(getFutureTimestamp().toInstant())
        .build();
  }

  public static AccessTokenCacheEntry createRandomAccessTokenCacheEntry() {
    return new AccessTokenCacheEntry.Builder()
        .linkedAccountId(1)
        .accessToken(UUID.randomUUID().toString())
        .expiresAt(getFutureTimestamp().toInstant())
        .build();
  }

  public static ProviderProperties createRandomProvider() {
    try {
      return ProviderProperties.create()
          .setAllowedRedirectUriPatterns(List.of(Pattern.compile("http://redirect")))
          .setAuthorizationEndpoint("http://authorize")
          .setClientId(UUID.randomUUID().toString())
          .setClientSecret(UUID.randomUUID().toString())
          .setIssuer("http://does/not/exist")
          .setLinkLifespan(Duration.ofDays(SecureRandom.getInstanceStrong().nextInt(10)))
          .setRevokeEndpoint("http://does/not/exist")
          .setTokenEndpoint("http://token")
          .setExternalIdClaim("preferred_username")
          .setUserNameAttributeName("username")
          .setUserInfoEndpoint("http://userinfo")
          .setScopes(List.of("scope1", "scope2"));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static VisaVerificationDetails createRandomVisaVerificationDetails() {
    return new VisaVerificationDetails.Builder()
        .visaId(21)
        .linkedAccountId(42)
        .provider(Provider.RAS)
        .visaJwt(UUID.randomUUID().toString())
        .build();
  }

  public static Throwable getRootCause(Throwable throwable) {
    // https://www.baeldung.com/java-exception-root-cause
    Objects.requireNonNull(throwable);
    Throwable rootCause = throwable;
    while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
      rootCause = rootCause.getCause();
    }
    return rootCause;
  }

  public static ClientRegistration createClientRegistration(Provider provider) {
    return ClientRegistration.withRegistrationId(provider.toString())
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .build();
  }
}
