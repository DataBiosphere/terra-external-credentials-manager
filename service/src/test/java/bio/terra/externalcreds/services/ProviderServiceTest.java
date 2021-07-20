package bio.terra.externalcreds.services;

import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.config.ProviderConfig.ProviderInfo;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSHeader.Builder;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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

  public MockWebServer mockBackEnd;

  private RSAKey rsaJWK;
  private JWKSet jwkSet;
  private JWSSigner signer;

  @BeforeEach
  void setUp() throws IOException, JOSEException {
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();

    rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
    jwkSet = new JWKSet(rsaJWK);

    // Create RSA-signer with the private key
    signer = new RSASSASigner(rsaJWK);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockBackEnd.shutdown();
  }

  @Test
  void testUseAuthorizationCodeToGetLinkedAccountNoVisas() throws JOSEException {
    var provider = "testProvider";
    var userId = "testUser";
    var authorizationCode = "testAuthCode";
    var redirectUri = "https://test/redirect/uri";
    var scopes = Set.of("email", "ga4gh");
    var state = "testState";
    var refreshToken = "testRefreshToken";
    var userEmail = "test@user.com";
    var issuer = "http://localhost:" + mockBackEnd.getPort();

    // Round the expiration to the nearest second because it will be rounded in the JWT.
    var passportExpires = new Date((new Date().getTime() + 60 * 1000) / 1000 * 1000);
    String jwtString =
        createPassportJwtString(issuer, userEmail, passportExpires, Collections.emptyList());

    var expectedLinkedAccount =
        LinkedAccount.builder()
            .providerId(provider)
            .userId(userId)
            .refreshToken(refreshToken)
            .externalUserId(userEmail)
            .build();
    var expectedPassport =
        GA4GHPassport.builder()
            .jwt(jwtString)
            .expires(new Timestamp(passportExpires.getTime()))
            .build();

    mockEverything(
        expectedLinkedAccount,
        expectedPassport,
        authorizationCode,
        redirectUri,
        scopes,
        state,
        issuer,
        1);

    var linkedAccountWithPassportAndVisas =
        providerService.useAuthorizationCodeToGetLinkedAccount(
            provider, userId, authorizationCode, redirectUri, scopes, state);

    Assertions.assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas.getLinkedAccount().withExpires(null));

    Assertions.assertEquals(expectedPassport, linkedAccountWithPassportAndVisas.getPassport());
    Assertions.assertTrue(linkedAccountWithPassportAndVisas.getVisas().isEmpty());
  }

  @Test
  void testUseAuthorizationCodeToGetLinkedAccountWithAccessTokenVisa()
      throws JOSEException, URISyntaxException {
    var provider = "testProvider";
    var userId = "testUser";
    var authorizationCode = "testAuthCode";
    var redirectUri = "https://test/redirect/uri";
    var scopes = Set.of("email", "ga4gh");
    var state = "testState";
    var refreshToken = "testRefreshToken";
    var userEmail = "test@user.com";
    var issuer = "http://localhost:" + mockBackEnd.getPort();
    // Round the expiration to the nearest second because it will be rounded in the JWT.
    var passportExpires = new Date((new Date().getTime() + 60 * 1000) / 1000 * 1000);

    var visa =
        GA4GHVisa.builder()
            .visaType("")
            .tokenType(TokenTypeEnum.access_token)
            .issuer(issuer)
            .expires(new Timestamp(passportExpires.getTime()))
            .build();
    visa = visa.withJwt(createVisaJwtString(visa));

    String jwtString =
        createPassportJwtString(issuer, userEmail, passportExpires, List.of(visa.getJwt()));

    var expectedLinkedAccount =
        LinkedAccount.builder()
            .providerId(provider)
            .userId(userId)
            .refreshToken(refreshToken)
            .externalUserId(userEmail)
            .build();
    var expectedPassport =
        GA4GHPassport.builder()
            .jwt(jwtString)
            .expires(new Timestamp(passportExpires.getTime()))
            .build();

    mockEverything(
        expectedLinkedAccount,
        expectedPassport,
        authorizationCode,
        redirectUri,
        scopes,
        state,
        issuer,
        2);

    var linkedAccountWithPassportAndVisas =
        providerService.useAuthorizationCodeToGetLinkedAccount(
            provider, userId, authorizationCode, redirectUri, scopes, state);

    Assertions.assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas.getLinkedAccount().withExpires(null));

    Assertions.assertEquals(expectedPassport, linkedAccountWithPassportAndVisas.getPassport());
    Assertions.assertEquals(
        visa, linkedAccountWithPassportAndVisas.getVisas().get(0).withLastValidated(null));
  }

  @Test
  void testUseAuthorizationCodeToGetLinkedAccountWithDocumentTokenVisa()
      throws JOSEException, URISyntaxException {
    var provider = "testProvider";
    var userId = "testUser";
    var authorizationCode = "testAuthCode";
    var redirectUri = "https://test/redirect/uri";
    var scopes = Set.of("email", "ga4gh");
    var state = "testState";
    var refreshToken = "testRefreshToken";
    var userEmail = "test@user.com";
    var issuer = "http://localhost:" + mockBackEnd.getPort();
    // Round the expiration to the nearest second because it will be rounded in the JWT.
    var passportExpires = new Date((new Date().getTime() + 60 * 1000) / 1000 * 1000);

    var visa =
        GA4GHVisa.builder()
            .visaType("")
            .tokenType(TokenTypeEnum.document_token)
            .issuer(issuer)
            .expires(new Timestamp(passportExpires.getTime()))
            .build();
    visa = visa.withJwt(createVisaJwtString(visa));

    String jwtString =
        createPassportJwtString(issuer, userEmail, passportExpires, List.of(visa.getJwt()));

    var expectedLinkedAccount =
        LinkedAccount.builder()
            .providerId(provider)
            .userId(userId)
            .refreshToken(refreshToken)
            .externalUserId(userEmail)
            .build();
    var expectedPassport =
        GA4GHPassport.builder()
            .jwt(jwtString)
            .expires(new Timestamp(passportExpires.getTime()))
            .build();

    mockEverything(
        expectedLinkedAccount,
        expectedPassport,
        authorizationCode,
        redirectUri,
        scopes,
        state,
        issuer,
        2);

    var linkedAccountWithPassportAndVisas =
        providerService.useAuthorizationCodeToGetLinkedAccount(
            provider, userId, authorizationCode, redirectUri, scopes, state);

    Assertions.assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas.getLinkedAccount().withExpires(null));

    Assertions.assertEquals(expectedPassport, linkedAccountWithPassportAndVisas.getPassport());
    Assertions.assertEquals(
        visa, linkedAccountWithPassportAndVisas.getVisas().get(0).withLastValidated(null));
  }

  @Test
  void testUseAuthorizationCodeToGetLinkedAccountWithNoPassport() {
    var provider = "testProvider";
    var userId = "testUser";
    var authorizationCode = "testAuthCode";
    var redirectUri = "https://test/redirect/uri";
    var scopes = Set.of("email", "ga4gh");
    var state = "testState";
    var refreshToken = "testRefreshToken";
    var userEmail = "test@user.com";
    var issuer = "http://localhost:" + mockBackEnd.getPort();

    var expectedLinkedAccount =
        LinkedAccount.builder()
            .providerId(provider)
            .userId(userId)
            .refreshToken(refreshToken)
            .externalUserId(userEmail)
            .build();

    mockEverything(
        expectedLinkedAccount, null, authorizationCode, redirectUri, scopes, state, issuer, 0);

    var linkedAccountWithPassportAndVisas =
        providerService.useAuthorizationCodeToGetLinkedAccount(
            provider, userId, authorizationCode, redirectUri, scopes, state);

    Assertions.assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas.getLinkedAccount().withExpires(null));

    Assertions.assertNull(linkedAccountWithPassportAndVisas.getPassport());
    Assertions.assertNull(linkedAccountWithPassportAndVisas.getVisas());
  }

  private void mockEverything(
      LinkedAccount linkedAccount,
      GA4GHPassport passport,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state,
      String issuer,
      int jwtCount) {
    var accessToken = "testAccessToken";

    var providerInfo = new ProviderInfo();
    providerInfo.setLinkLifespan(Duration.ZERO);

    when(providerConfig.getServices())
        .thenReturn(Map.of(linkedAccount.getProviderId(), providerInfo));
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProviderId())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();
    when(providerClientCache.getProviderClient(linkedAccount.getProviderId()))
        .thenReturn(providerClient);

    var accessTokenResponse =
        OAuth2AccessTokenResponse.withToken(accessToken)
            .refreshToken(linkedAccount.getRefreshToken())
            .tokenType(TokenType.BEARER)
            .build();

    when(oAuth2Service.authorizationCodeExchange(
            providerClient, authorizationCode, redirectUri, scopes, state, null))
        .thenReturn(accessTokenResponse);

    Map<String, Object> userAttributes = new HashMap<>();
    userAttributes.put(ProviderService.EXTERNAL_USERID_ATTR, linkedAccount.getExternalUserId());
    if (passport != null) {
      userAttributes.put(ProviderService.PASSPORT_JWT_V11_CLAIM, passport.getJwt());
    }

    var wellKnownConfigMap =
        Map.of(
            "issuer",
            issuer,
            "jwks_uri",
            String.format("http://localhost:%d/openid/connect/jwks.json", mockBackEnd.getPort()));

    for (int i = 0; i < jwtCount; i++) {
      enqueueResponsesForJwtValidation(wellKnownConfigMap);
    }

    OAuth2User user =
        new DefaultOAuth2User(null, userAttributes, ProviderService.EXTERNAL_USERID_ATTR);

    when(oAuth2Service.getUserInfo(providerClient, accessTokenResponse.getAccessToken()))
        .thenReturn(user);
  }

  private void enqueueResponsesForJwtValidation(Map<String, String> wellKnownConfigMap) {
    mockBackEnd.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(JSONObjectUtils.toJSONString(wellKnownConfigMap)));

    mockBackEnd.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(jwkSet.toString()));

    mockBackEnd.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(jwkSet.toString()));
  }

  private String createPassportJwtString(
      String issuer, String email, Date expires, List<String> visaJwts) throws JOSEException {

    JWTClaimsSet.Builder passportClaimSetBuilder =
        new JWTClaimsSet.Builder()
            .subject(UUID.randomUUID().toString())
            .claim(ProviderService.EXTERNAL_USERID_ATTR, email)
            .issuer(issuer)
            .expirationTime(expires);

    if (!visaJwts.isEmpty()) {
      passportClaimSetBuilder.claim(ProviderService.GA4GH_PASSPORT_V1_CLAIM, visaJwts);
    }

    JWTClaimsSet claimsSet = passportClaimSetBuilder.build();

    SignedJWT signedJWT =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(), claimsSet);

    signedJWT.sign(signer);

    return signedJWT.serialize();
  }

  private String createVisaJwtString(GA4GHVisa visa) throws URISyntaxException, JOSEException {
    JWTClaimsSet visaClaimSet =
        new JWTClaimsSet.Builder()
            .expirationTime(visa.getExpires())
            .issuer(visa.getIssuer())
            .claim(ProviderService.VISA_TYPE_CLAIM, visa.getVisaType())
            .build();

    Builder jwtHeaderBuilder = new Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID());
    if (visa.getTokenType() == TokenTypeEnum.document_token) {
      jwtHeaderBuilder.jwkURL(new URI(visa.getIssuer()));
    }
    SignedJWT signedVisaJwt = new SignedJWT(jwtHeaderBuilder.build(), visaClaimSet);
    signedVisaJwt.sign(signer);
    return signedVisaJwt.serialize();
  }
}
