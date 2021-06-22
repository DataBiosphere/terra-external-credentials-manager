package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderConfig;
import java.util.Collections;
import java.util.Set;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.stereotype.Service;

@Service
public class ProviderService {

  private final ProviderConfig providerConfig;
  private final ProviderClientCache providerClientCache;

  public ProviderService(ProviderConfig providerConfig, ProviderClientCache providerClientCache) {
    this.providerConfig = providerConfig;
    this.providerClientCache = providerClientCache;
  }

  public Set<String> getProviderList() {
    return Collections.unmodifiableSet(providerConfig.getServices().keySet());
  }

  public String getProviderAuthorizationUrl(
      String provider, String redirectUri, Set<String> scopes, String state) {
    ProviderConfig.ProviderInfo providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      return null;
    }

    ClientRegistration providerClient = providerClientCache.getProviderClient(provider);

    OAuth2AuthorizationRequest authRequest =
        createOAuth2AuthorizationRequest(redirectUri, scopes, state, providerInfo, providerClient);

    return authRequest.getAuthorizationRequestUri();
  }

  private OAuth2AuthorizationRequest createOAuth2AuthorizationRequest(
      String redirectUri,
      Set<String> scopes,
      String state,
      ProviderConfig.ProviderInfo providerInfo,
      ClientRegistration providerClient) {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri(providerClient.getProviderDetails().getAuthorizationUri())
        .redirectUri(redirectUri)
        .clientId(providerInfo.getClientId())
        .scopes(scopes)
        .state(state)
        .additionalParameters(providerInfo.getAdditionalAuthorizationParameters())
        .build();
  }

  public OAuth2AccessTokenResponse authorizationCodeExchange(
      String provider,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state) {

    ProviderConfig.ProviderInfo providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      throw new RuntimeException("unknown provider");
    }

    ClientRegistration providerClient = providerClientCache.getProviderClient(provider);
    OAuth2AuthorizationRequest authRequest =
        createOAuth2AuthorizationRequest(redirectUri, scopes, state, providerInfo, providerClient);
    OAuth2AuthorizationResponse authResponse =
        OAuth2AuthorizationResponse.success(authorizationCode)
            .redirectUri(redirectUri)
            .state(state)
            .build();

    DefaultAuthorizationCodeTokenResponseClient tokenResponseClient =
        new DefaultAuthorizationCodeTokenResponseClient();
    OAuth2AuthorizationCodeGrantRequest codeGrantRequest =
        new OAuth2AuthorizationCodeGrantRequest(
            providerClient, new OAuth2AuthorizationExchange(authRequest, authResponse));
    OAuth2AccessTokenResponse tokenResponse =
        tokenResponseClient.getTokenResponse(codeGrantRequest);

    return tokenResponse;
  }
}
