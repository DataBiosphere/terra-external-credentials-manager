package bio.terra.externalcreds.services;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Service that encapsulates all OAuth2 features need by ECM. General flow: 1)
 * getAuthorizationRequestUri - visit to authenticate with provider 2) authorizationCodeExchange -
 * using code resulting from authentication, get access and refresh tokens 3) getUserInfo - using
 * access token call the user info endpoint - called periodically to get updated user info 4)
 * authorizeWithRefreshToken - get a new access token using a refresh token when required
 */
@Service
public class OAuth2Service {
  /**
   * Construct authorization uri user should visit to authenticate
   *
   * @param providerClient identity provider client, see {@link ProviderClientCache}
   * @param redirectUri uri the user will be directed to after authentication
   * @param scopes scopes requested for authentication
   * @param state oauth thing
   * @param additionalAuthorizationParameters any other parameters
   * @return uri to direct user to
   */
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

  /**
   * After authentication, the resulting code should be used here
   *
   * @param providerClient identity provider client, see {@link ProviderClientCache}
   * @param redirectUri uri the user will be directed to after authentication
   * @param scopes scopes requested for authentication
   * @param state oauth thing
   * @param additionalAuthorizationParameters any other parameters
   * @param authorizationCode code resulting from authentication
   * @return token response containing access and refresh tokens
   */
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

  /**
   * Given an refresh token, get an access token
   *
   * @param providerClient identity provider client, see {@link ProviderClientCache}
   * @param refreshToken
   * @return token response containing access and refresh tokens, note that if there is a refresh
   *     token in this response it should replace the original refresh token which is likely invalid
   */
  public OAuth2AccessTokenResponse authorizeWithRefreshToken(
      ClientRegistration providerClient, OAuth2RefreshToken refreshToken) {
    // the OAuth2RefreshTokenGrantRequest requires an access token to be specified but a valid one
    OAuth2AccessToken dummyAccessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "dummy", Instant.EPOCH, Instant.now());

    OAuth2RefreshTokenGrantRequest refreshTokenGrantRequest =
        new OAuth2RefreshTokenGrantRequest(providerClient, dummyAccessToken, refreshToken);

    DefaultRefreshTokenTokenResponseClient refreshTokenTokenResponseClient =
        new DefaultRefreshTokenTokenResponseClient();

    return refreshTokenTokenResponseClient.getTokenResponse(refreshTokenGrantRequest);
  }

  public OAuth2User getUserInfo(ClientRegistration providerClient, OAuth2AccessToken accessToken) {
    OAuth2UserRequest userRequest = new OAuth2UserRequest(providerClient, accessToken);
    return new DefaultOAuth2UserService().loadUser(userRequest);
  }
}
