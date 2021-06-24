package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.stereotype.Component;

/**
 * Creating a provider client requires an api call to [provider
 * issuer]/.well-known/openid-configuration. That information almost never changes and we don't want
 * to hammer that api. Therefore this cache.
 */
@Component
public class ProviderClientCache {
  private final ProviderConfig providerConfig;

  public ProviderClientCache(ProviderConfig providerConfig) {
    this.providerConfig = providerConfig;
  }

  @Cacheable(cacheNames = "providerClients", sync = true)
  public ClientRegistration getProviderClient(String provider) {
    ProviderConfig.ProviderInfo providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      return null;
    }

    return ClientRegistrations.fromOidcIssuerLocation(providerInfo.getIssuer())
        .clientId(providerInfo.getClientId())
        .clientSecret(providerInfo.getClientSecret())
        .issuerUri(providerInfo.getIssuer())
        .build();
  }
}
