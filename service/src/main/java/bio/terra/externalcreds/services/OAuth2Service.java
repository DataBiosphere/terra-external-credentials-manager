package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.models.LinkedAccount;
import com.nimbusds.oauth2.sdk.TokenRevocationRequest;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
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
 * Service that encapsulates all OAuth2 features need by ECM. General flow:
 *
 * <ol>
 *   <li>getAuthorizationRequestUri - visit to authenticate with provider
 *   <li>authorizationCodeExchange - using code resulting from authentication, get access and
 *       refresh tokens
 *   <li>getUserInfo - using access token call the user info endpoint - called periodically to get
 *       updated user info
 *   <li>authorizeWithRefreshToken - get a new access token using a refresh token when required
 * </ol>
 */
@Service
@Slf4j
public class OAuth2Service {
  /**
   * Construct authorization uri user should visit to authenticate
   *
   * @param providerClient identity provider client, see {@link ProviderOAuthClientCache}
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
   * @param providerClient identity provider client, see {@link ProviderOAuthClientCache}
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

    var authRequest =
        createOAuth2AuthorizationRequest(
            redirectUri, scopes, state, providerClient, additionalAuthorizationParameters);

    var authResponse =
        OAuth2AuthorizationResponse.success(authorizationCode)
            .redirectUri(redirectUri)
            .state(state)
            .build();

    var tokenResponseClient = new DefaultAuthorizationCodeTokenResponseClient();
    var codeGrantRequest =
        new OAuth2AuthorizationCodeGrantRequest(
            providerClient, new OAuth2AuthorizationExchange(authRequest, authResponse));

    return tokenResponseClient.getTokenResponse(codeGrantRequest);
  }

  /**
   * Given a refresh token, get an access token
   *
   * @param providerClient identity provider client, see {@link ProviderOAuthClientCache}
   * @param refreshToken
   * @return token response containing access and refresh tokens, note that if there is a refresh
   *     token in this response it should replace the original refresh token which is likely invalid
   */
  public OAuth2AccessTokenResponse authorizeWithRefreshToken(
      ClientRegistration providerClient, OAuth2RefreshToken refreshToken, Set<String> scopes) {
    // the OAuth2RefreshTokenGrantRequest requires an access token to be specified but
    // it does not have to be a valid one so create a dummy
    var dummyAccessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "dummy", Instant.EPOCH, Instant.now());

    var refreshTokenGrantRequest =
        new OAuth2RefreshTokenGrantRequest(providerClient, dummyAccessToken, refreshToken, scopes);

    var refreshTokenTokenResponseClient = new DefaultRefreshTokenTokenResponseClient();

    return refreshTokenTokenResponseClient.getTokenResponse(refreshTokenGrantRequest);
  }

  public OAuth2User getUserInfo(ClientRegistration providerClient, OAuth2AccessToken accessToken) {
    var userRequest = new OAuth2UserRequest(providerClient, accessToken);
    return new DefaultOAuth2UserService().loadUser(userRequest);
  }

  /**
   * Revoke the refresh token associated with the linked account. This is a best attempt and will
   * not throw an exception if it fails.
   *
   * @param providerProperties provider properties
   * @param linkedAccount linked account
   */
  public void revokeRefreshToken(
      ProviderProperties providerProperties, LinkedAccount linkedAccount) {
    String revokeEndpoint = providerProperties.getRevokeEndpoint();

    // spring security does not seem to have a revocation client so use nimbus
    var req =
        new TokenRevocationRequest(
            URI.create(revokeEndpoint),
            new ClientSecretBasic(
                new ClientID(providerProperties.getClientId()),
                new Secret(providerProperties.getClientSecret())),
            new RefreshToken(linkedAccount.getRefreshToken()));
    try {
      var response = req.toHTTPRequest().send();
      log.info(
          "Token revocation request for user [{}], provider [{}] returned status [{}] with the result: [{}]",
          linkedAccount.getUserId(),
          linkedAccount.getProvider().toString(),
          response.getStatusCode(),
          response.getContent());
    } catch (IOException e) {
      log.error("revoke refresh token failure", e);
    }
  }
}
