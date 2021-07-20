package bio.terra.externalcreds.services;

import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.config.ProviderConfig.ProviderInfo;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class ProviderServiceTest extends BaseTest {

  @MockBean OAuth2Service oAuth2Service;
  @MockBean ProviderClientCache providerClientCache;
  @MockBean ProviderConfig providerConfig;

  @Autowired ProviderService providerService;

  public static MockWebServer mockBackEnd;

  @BeforeAll
  static void setUp() throws IOException {
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();
  }

  @AfterAll
  static void tearDown() throws IOException {
    mockBackEnd.shutdown();
  }

  @Test
  void testUseAuthorizationCodeToGetLinkedAccount() throws JOSEException {
    var provider = "testProvider";
    var userId = "testUser";
    var authorizationCode = "testAuthCode";
    var redirectUri = "https://test/redirect/uri";
    var scopes = Set.of("email", "ga4gh");
    var state = "testState";
    var accessToken = "testAccessToken";
    var refreshToken = "testRefreshToken";
    var userEmail = "test@user.com";
    var issuer = "http://localhost:" + this.mockBackEnd.getPort();

    var providerInfo = new ProviderInfo();
    providerInfo.setLinkLifespan(Duration.ZERO);

    when(providerConfig.getServices()).thenReturn(Map.of(provider, providerInfo));
    var providerClient =
        ClientRegistration.withRegistrationId(provider)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();
    when(providerClientCache.getProviderClient(provider)).thenReturn(providerClient);

    var accessTokenResponse =
        OAuth2AccessTokenResponse.withToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType(TokenType.BEARER)
            .build();

    when(oAuth2Service.authorizationCodeExchange(
            providerClient, authorizationCode, redirectUri, scopes, state, null))
        .thenReturn(accessTokenResponse);

    Map<String, Object> userAttributes =
        Map.of(
            ProviderService.EXTERNAL_USERID_ATTR,
            userEmail,
            ProviderService.PASSPORT_JWT_V11_CLAIM,
            createJwtString(issuer, userEmail));

    var wellKnownConfigMap =
        Map.of(
            "issuer",
            issuer,
            "jwks_uri",
            String.format(
                "http://localhost:%d/openid/connect/jwks.json", this.mockBackEnd.getPort()));
    this.mockBackEnd.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(JSONObjectUtils.toJSONString(wellKnownConfigMap)));

    OAuth2User user =
        new DefaultOAuth2User(null, userAttributes, ProviderService.EXTERNAL_USERID_ATTR);

    when(oAuth2Service.getUserInfo(providerClient, accessTokenResponse.getAccessToken()))
        .thenReturn(user);

    var linkedAccountWithPassportAndVisas =
        providerService.useAuthorizationCodeToGetLinkedAccount(
            provider, userId, authorizationCode, redirectUri, scopes, state);
  }

  private String createJwtString(String issuer, String email) throws JOSEException {
    RSAKey rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();

    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject(UUID.randomUUID().toString())
            .claim(ProviderService.EXTERNAL_USERID_ATTR, email)
            .issuer(issuer)
            .expirationTime(new Date(new Date().getTime() + 60 * 1000))
            .build();

    SignedJWT signedJWT =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(), claimsSet);

    // Create RSA-signer with the private key
    JWSSigner signer = new RSASSASigner(rsaJWK);
    signedJWT.sign(signer);

    return signedJWT.serialize();
  }
}
