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
 * to hammer that api. This cache is for the client info for exchanging refresh tokens for access
 * tokens.
 *
 * <p>The cache is reset every 6 hours to detect infrequent changes.
 */
@Component
@Slf4j
public class ProviderTokenClientCache {
  private final ExternalCredsConfig externalCredsConfig;

  public ProviderTokenClientCache(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  @Cacheable(cacheNames = "providerTokenClients", sync = true)
  public ClientRegistration getProviderClient(Provider provider) {
    log.info("Loading ProviderTokenClient {}", provider);
    var providerInfo = externalCredsConfig.getProviderProperties(provider);

    ClientRegistration.Builder builder =
        switch (provider) {
          case RAS, FENCE, DCF_FENCE, ANVIL, KIDS_FIRST -> ClientRegistrations
              .fromOidcIssuerLocation(providerInfo.getIssuer())
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
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientId(providerInfo.getClientId())
                .clientSecret(providerInfo.getClientSecret())
                .issuerUri(providerInfo.getIssuer())
                .redirectUri(redirectUri)
                .userNameAttributeName(providerInfo.getUserNameAttributeName());
          }
          case ERA_COMMONS -> {
            if (externalCredsConfig.getEraCommonsLinkingEnabled()) {
              yield ClientRegistrations.fromOidcIssuerLocation(providerInfo.getIssuer())
                  .clientId(providerInfo.getClientId())
                  .clientSecret(providerInfo.getClientSecret())
                  .issuerUri(providerInfo.getIssuer());
            } else {
              throw new UnsupportedOperationException("eRA Commons does not support OAuth (yet)");
            }
          }
        };

    // set optional overrides
    providerInfo.getUserInfoEndpoint().ifPresent(builder::userInfoUri);
    providerInfo.getAuthorizationEndpoint().ifPresent(builder::authorizationUri);
    providerInfo.getTokenEndpoint().ifPresent(builder::tokenUri);
    providerInfo.getJwksUri().ifPresent(builder::jwkSetUri);

    return builder.build();
  }

  @Scheduled(fixedRateString = "6", timeUnit = TimeUnit.HOURS)
  @CacheEvict(allEntries = true, cacheNames = "providerTokenClients")
  public void resetCache() {
    log.info("ProviderTokenClientCache reset");
  }
}
