package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ProviderProperties;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
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
public class ProviderClientCache {
  private final ExternalCredsConfig externalCredsConfig;

  public ProviderClientCache(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  @Cacheable(cacheNames = "providerClients", sync = true)
  public Optional<ClientRegistration> getProviderClient(String providerName) {
    log.info("Loading ProviderClient {}", providerName);
    return Optional.ofNullable(externalCredsConfig.getProviders().get(providerName))
        .map(this::buildClientRegistration);
  }

  @Scheduled(fixedRateString = "6", timeUnit = TimeUnit.HOURS)
  @CacheEvict(allEntries = true, cacheNames = "providerClients")
  public void resetCache() {
    log.info("ProviderClientCache reset");
  }

  private ClientRegistration buildClientRegistration(ProviderProperties providerInfo) {
    var builder =
        ClientRegistrations.fromOidcIssuerLocation(providerInfo.getIssuer())
            .clientId(providerInfo.getClientId())
            .clientSecret(providerInfo.getClientSecret())
            .issuerUri(providerInfo.getIssuer());

    // set optional overrides
    providerInfo.getUserInfoEndpoint().ifPresent(builder::userInfoUri);
    providerInfo.getAuthorizationEndpoint().ifPresent(builder::authorizationUri);
    providerInfo.getTokenEndpoint().ifPresent(builder::tokenUri);
    providerInfo.getJwksUri().ifPresent(builder::jwkSetUri);

    return builder.build();
  }
}
