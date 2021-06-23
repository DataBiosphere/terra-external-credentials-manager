package bio.terra.externalcreds.services;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.stereotype.Service;

/** */
@Service
public class OAuth2Service {
  public OAuth2Service() {}

  public String getAuthorizationRequestUri(
      ClientRegistration providerClient,
      String redirectUri,
      Set<String> scopes,
      String state,
      Map<String, Object> additionalAuthorizationParameters) {

    return createOAuth2AuthorizationRequest(
            redirectUri, scopes, state, providerClient, additionalAuthorizationParameters)
        .getAuthorizationRequestUri();
  }

  private OAuth2AuthorizationRequest createOAuth2AuthorizationRequest(
      String redirectUri,
      Set<String> scopes,
      String state,
      ClientRegistration providerClient,
      Map<String, Object> additionalAuthorizationParameters) {

    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri(providerClient.getProviderDetails().getAuthorizationUri())
        .redirectUri(redirectUri)
        .clientId(providerClient.getClientId())
        .scopes(scopes)
        .state(state)
        .additionalParameters(additionalAuthorizationParameters)
        .build();
  }

  public OAuth2AccessTokenResponse authorizationCodeExchange(
      ClientRegistration providerClient,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state,
      Map<String, Object> additionalAuthorizationParameters) {

    OAuth2AuthorizationRequest authRequest =
        createOAuth2AuthorizationRequest(
            redirectUri, scopes, state, providerClient, additionalAuthorizationParameters);

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

  public OAuth2AccessTokenResponse authorizeWithRefreshToken(
      ClientRegistration providerClient, OAuth2RefreshToken refreshToken) {
    OAuth2AccessToken dummyAccessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "dummy", Instant.EPOCH, Instant.now());
    OAuth2RefreshTokenGrantRequest refreshTokenGrantRequest =
        new OAuth2RefreshTokenGrantRequest(providerClient, dummyAccessToken, refreshToken);
    DefaultRefreshTokenTokenResponseClient refreshTokenTokenResponseClient =
        new DefaultRefreshTokenTokenResponseClient();
    return refreshTokenTokenResponseClient.getTokenResponse(refreshTokenGrantRequest);
  }
}
