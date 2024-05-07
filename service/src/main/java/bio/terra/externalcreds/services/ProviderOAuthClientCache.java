package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;

/**
 * Creating a provider client requires an api call to [provider
 * issuer]/.well-known/openid-configuration. That information almost never changes and we don't want
 * to hammer that api. Therefore this cache.
 *
 * <p>The cache is reset every 30 minutes to detect infrequent changes and service outages.
 */
@Component
@Slf4j
public class ProviderOAuthClientCache {
  private final ExternalCredsConfig externalCredsConfig;

  public ProviderOAuthClientCache(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  @Cacheable(cacheNames = "providerOAuthClients", sync = true)
  public ClientRegistration getProviderClient(Provider provider) {
    log.info("Loading ProviderOAuthClient {}", provider);
    var providerInfo = externalCredsConfig.getProviderProperties(provider);

    ClientRegistration.Builder builder =
        switch (provider) {
          case RAS -> ClientRegistrations.fromOidcIssuerLocation(providerInfo.getIssuer())
              .clientId(providerInfo.getClientId())
              .clientSecret(providerInfo.getClientSecret())
              .issuerUri(providerInfo.getIssuer());
          case GITHUB -> {
            String redirectUri =
                providerInfo.getAllowedRedirectUriPatterns().stream()
                    .map(Pattern::toString)
                    .toList()
                    .get(0);
            yield ClientRegistration.withRegistrationId(provider.toString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId(providerInfo.getClientId())
                .clientSecret(providerInfo.getClientSecret())
                .issuerUri(providerInfo.getIssuer())
                .redirectUri(redirectUri)
                .userNameAttributeName(providerInfo.getUserNameAttributeName());
          }
          case FENCE, DCF_FENCE, ANVIL, KIDS_FIRST -> ClientRegistrations.fromOidcIssuerLocation(
                  providerInfo.getIssuer())
              .clientId(providerInfo.getClientId())
              .clientSecret(providerInfo.getClientSecret())
              .issuerUri(providerInfo.getIssuer())
              .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
        };

    // set optional overrides
    providerInfo.getUserInfoEndpoint().ifPresent(builder::userInfoUri);
    providerInfo.getAuthorizationEndpoint().ifPresent(builder::authorizationUri);
    providerInfo.getTokenEndpoint().ifPresent(builder::tokenUri);
    providerInfo.getJwksUri().ifPresent(builder::jwkSetUri);

    return builder.build();
  }

  @Scheduled(fixedRateString = "30", timeUnit = TimeUnit.MINUTES)
  @CacheEvict(allEntries = true, cacheNames = "providerOAuthClients")
  public void resetCache() {
    log.info("ProviderOAuthClientCache reset");
  }
}
