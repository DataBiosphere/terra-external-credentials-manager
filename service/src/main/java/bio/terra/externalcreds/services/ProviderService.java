package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderConfig;
import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ProviderService {

  private final ProviderConfig providerConfig;
  private final ProviderClientCache providerClientCache;
  private final OAuth2Service oAuth2Service;

  public ProviderService(
      ProviderConfig providerConfig,
      ProviderClientCache providerClientCache,
      OAuth2Service oAuth2Service) {
    this.providerConfig = providerConfig;
    this.providerClientCache = providerClientCache;
    this.oAuth2Service = oAuth2Service;
  }

  public Set<String> getProviderList() {
    return Collections.unmodifiableSet(providerConfig.getServices().keySet());
  }

  public String getProviderAuthorizationUrl(
      String provider, String redirectUri, Set<String> scopes, String state) {
    val providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      return null;
    }

    val providerClient = providerClientCache.getProviderClient(provider);

    return oAuth2Service.getAuthorizationRequestUri(
        providerClient,
        redirectUri,
        scopes,
        state,
        providerInfo.getAdditionalAuthorizationParameters());
  }
}
