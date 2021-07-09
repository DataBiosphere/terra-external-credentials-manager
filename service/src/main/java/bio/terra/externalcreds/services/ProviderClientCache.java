package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderConfig;
import lombok.val;
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
    val providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      return null;
    }

    val builder =
        ClientRegistrations.fromOidcIssuerLocation(providerInfo.getIssuer())
            .clientId(providerInfo.getClientId())
            .clientSecret(providerInfo.getClientSecret())
            .issuerUri(providerInfo.getIssuer());

    // set optional overrides
    providerInfo.getUserInfoEndpoint().map(builder::userInfoUri);
    providerInfo.getAuthorizationEndpoint().map(builder::authorizationUri);
    providerInfo.getTokenEndpoint().map(builder::tokenUri);
    providerInfo.getJwksUri().map(builder::jwkSetUri);

    return builder.build();
  }
}
