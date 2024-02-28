package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.generated.model.Provider;
import java.util.Optional;
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
 * <p>The cache is reset every 6 hours to detect infrequent changes.
 */
@Component
@Slf4j
public class ProviderOAuthClientCache {
  private final ExternalCredsConfig externalCredsConfig;

  public ProviderOAuthClientCache(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  @Cacheable(cacheNames = "providerOAuthClients", sync = true)
  public Optional<ClientRegistration> getProviderClient(String providerName) {
    log.info("Loading ProviderOAuthClient {}", providerName);
    return Optional.ofNullable(externalCredsConfig.getProviders().get(providerName))
        .map(p -> buildClientRegistration(providerName, p));
  }

  @Scheduled(fixedRateString = "6", timeUnit = TimeUnit.HOURS)
  @CacheEvict(allEntries = true, cacheNames = "providerOAuthClients")
  public void resetCache() {
    log.info("ProviderOAuthClientCache reset");
  }

  public ClientRegistration buildClientRegistration(
      String providerName, ProviderProperties providerInfo) {
    Provider provider = Provider.fromValue(providerName);
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
            yield ClientRegistration.withRegistrationId(providerName)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId(providerInfo.getClientId())
                .clientSecret(providerInfo.getClientSecret())
                .issuerUri(providerInfo.getIssuer())
                .redirectUri(redirectUri)
                .userNameAttributeName(providerInfo.getUserNameAttributeName());
          }
        };

    // set optional overrides
    providerInfo.getUserInfoEndpoint().ifPresent(builder::userInfoUri);
    providerInfo.getAuthorizationEndpoint().ifPresent(builder::authorizationUri);
    providerInfo.getTokenEndpoint().ifPresent(builder::tokenUri);
    providerInfo.getJwksUri().ifPresent(builder::jwkSetUri);

    return builder.build();
  }
}
