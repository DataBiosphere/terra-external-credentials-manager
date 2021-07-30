package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class AuthorizationCodeExchangeTest extends BaseTest {

  public static final String JWKS_PATH = "/openid/connect/jwks.json";
  public static final String JKU_PATH = "/jku.json";

  @MockBean OAuth2Service oAuth2Service;
  @MockBean ProviderClientCache providerClientCache;
  @MockBean ExternalCredsConfig externalCredsConfig;

  @Autowired ProviderService providerService;

  private static ClientAndServer mockServer;
  private static RSAKey accessTokenRsaJWK;
  private static JWSSigner accessTokenSigner;
  private static RSAKey documentTokenRsaJWK;
  private static JWSSigner documentTokenSigner;
  private static String issuer;

  private final String authorizationCode = UUID.randomUUID().toString();
  private final String redirectUri = "https://test/redirect/uri";
  private final Set<String> scopes = Set.of("email", "ga4gh");
  private final String state = UUID.randomUUID().toString();
  private final String userEmail = "test@user.com";
  // Round the expiration to the nearest second because it will be rounded in the JWT.
  private final Date passportExpires = new Date((new Date().getTime() + 60 * 1000) / 1000 * 1000);
  private final Timestamp passportExpiresTime = new Timestamp(passportExpires.getTime());

  @BeforeAll
  static void setUpJwtVerification() throws JOSEException {
    accessTokenRsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
    documentTokenRsaJWK = new RSAKeyGenerator(2048).keyID("456").generate();

    // Create RSA-signer with the private key
    accessTokenSigner = new RSASSASigner(accessTokenRsaJWK);
    documentTokenSigner = new RSASSASigner(documentTokenRsaJWK);

    mockServer = ClientAndServer.startClientAndServer(50555);

    issuer = "http://localhost:" + mockServer.getPort();
    var wellKnownConfigMap = Map.of("issuer", issuer, "jwks_uri", issuer + JWKS_PATH);

    mockServer
        .when(HttpRequest.request("/.well-known/openid-configuration").withMethod("GET"))
        .respond(
            HttpResponse.response(JSONObjectUtils.toJSONString(wellKnownConfigMap))
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON));

    mockServer
        .when(HttpRequest.request(JWKS_PATH).withMethod("GET"))
        .respond(
            HttpResponse.response(new JWKSet(accessTokenRsaJWK).toString())
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON));

    mockServer
        .when(HttpRequest.request(JKU_PATH).withMethod("GET"))
        .respond(
            HttpResponse.response(new JWKSet(documentTokenRsaJWK).toString())
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON));
  }

  @AfterAll
  static void tearDown() {
    mockServer.stop();
  }

  @Test
  void testNoVisas() throws JOSEException {
    var expectedPassport = createTestPassport(Collections.emptyList());
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, Collections.emptyList());
  }

  @Test
  void testAccessTokenVisa() throws JOSEException, URISyntaxException {
    var visa = createTestVisa(TokenTypeEnum.access_token);
    var expectedPassport = createTestPassport(List.of(visa));
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, List.of(visa));
  }

  @Test
  void testDocumentTokenVisa() throws JOSEException, URISyntaxException {
    var visa = createTestVisa(TokenTypeEnum.document_token);
    var expectedPassport = createTestPassport(List.of(visa));
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, List.of(visa));
  }

  @Test
  void testMultipleVisas() throws URISyntaxException, JOSEException {
    var visas =
        List.of(
            createTestVisa(TokenTypeEnum.document_token),
            createTestVisa(TokenTypeEnum.access_token),
            createTestVisa(TokenTypeEnum.document_token),
            createTestVisa(TokenTypeEnum.access_token));
    var expectedPassport = createTestPassport(visas);
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, visas);
  }

  @Test
  void testNoPassport() {
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, null, Collections.emptyList());
  }

  @Test
  void testInvalidJwtSignature() throws URISyntaxException, JOSEException {
    var invalidJwt = createTestVisa(TokenTypeEnum.access_token).getJwt() + "foo";
    assertThrows(InvalidJwtException.class, () -> providerService.decodeJwt(invalidJwt));
  }

  @Test
  void testJwtMissingIssuer() throws URISyntaxException, JOSEException {
    var jwtMissingIssuer =
        createVisaJwtString(createTestVisa(TokenTypeEnum.access_token).withIssuer("null"));
    assertThrows(InvalidJwtException.class, () -> providerService.decodeJwt(jwtMissingIssuer));
  }

  @Test
  void testJwtIssuerNotReal() throws URISyntaxException, JOSEException {
    var jwtNotRealIssuer =
        createVisaJwtString(
            createTestVisa(TokenTypeEnum.access_token).withIssuer("http://does.not.exist"));
    assertThrows(InvalidJwtException.class, () -> providerService.decodeJwt(jwtNotRealIssuer));
  }

  @Test
  void testJwtJkuNotResponsive() throws URISyntaxException, JOSEException {
    var jwtNotResponsiveJku =
        createVisaJwtString(
            createTestVisa(TokenTypeEnum.access_token).withIssuer("http://localhost:10"));
    assertThrows(InvalidJwtException.class, () -> providerService.decodeJwt(jwtNotResponsiveJku));
  }

  @Test
  void testJwtJkuMalformed() throws URISyntaxException, JOSEException {
    var jwtMalformedJku =
        createVisaJwtString(createTestVisa(TokenTypeEnum.access_token).withIssuer("foobar"));
    assertThrows(InvalidJwtException.class, () -> providerService.decodeJwt(jwtMalformedJku));
  }

  private void setupMocks(
      LinkedAccount linkedAccount,
      GA4GHPassport passport,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state) {
    var providerInfo = TestUtils.createRandomProvider();
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProviderId())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();
    var accessTokenResponse =
        OAuth2AccessTokenResponse.withToken(UUID.randomUUID().toString())
            .refreshToken(linkedAccount.getRefreshToken())
            .tokenType(TokenType.BEARER)
            .build();
    var userAttributes = new HashMap<String, Object>();
    userAttributes.put(ProviderService.EXTERNAL_USERID_ATTR, linkedAccount.getExternalUserId());
    if (passport != null) {
      userAttributes.put(ProviderService.PASSPORT_JWT_V11_CLAIM, passport.getJwt());
    }
    OAuth2User user =
        new DefaultOAuth2User(null, userAttributes, ProviderService.EXTERNAL_USERID_ATTR);

    when(externalCredsConfig.getProviders())
        .thenReturn(Map.of(linkedAccount.getProviderId(), providerInfo));
    when(providerClientCache.getProviderClient(linkedAccount.getProviderId()))
        .thenReturn(providerClient);
    when(oAuth2Service.authorizationCodeExchange(
            providerClient,
            authorizationCode,
            redirectUri,
            scopes,
            state,
            providerInfo.getAdditionalAuthorizationParameters()))
        .thenReturn(accessTokenResponse);
    when(oAuth2Service.getUserInfo(providerClient, accessTokenResponse.getAccessToken()))
        .thenReturn(user);
  }

  private void runTest(
      LinkedAccount expectedLinkedAccount,
      GA4GHPassport expectedPassport,
      List<GA4GHVisa> expectedVisas) {

    setupMocks(
        expectedLinkedAccount, expectedPassport, authorizationCode, redirectUri, scopes, state);

    var linkedAccountWithPassportAndVisas =
        providerService.createLink(
            expectedLinkedAccount.getProviderId(),
            expectedLinkedAccount.getUserId(),
            authorizationCode,
            redirectUri,
            scopes,
            state);

    assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas
            .getLinkedAccount()
            .withExpires(passportExpiresTime)
            .withId(Optional.empty()));

    var stablePassport =
        linkedAccountWithPassportAndVisas
            .getPassport()
            .map(p -> p.withId(Optional.empty()).withLinkedAccountId(Optional.empty()));
    assertEquals(Optional.ofNullable(expectedPassport), stablePassport);

    var stableVisas =
        linkedAccountWithPassportAndVisas.getVisas() == null
            ? null
            : linkedAccountWithPassportAndVisas.getVisas().stream()
                .map(
                    visa ->
                        visa.withLastValidated(Optional.empty())
                            .withId(Optional.empty())
                            .withPassportId(Optional.empty()))
                .collect(Collectors.toList());
    assertEquals(expectedVisas, stableVisas);
  }

  private String createPassportJwtString(Date expires, List<String> visaJwts) throws JOSEException {

    var passportClaimSetBuilder =
        new JWTClaimsSet.Builder()
            .subject(UUID.randomUUID().toString())
            .claim(ProviderService.EXTERNAL_USERID_ATTR, userEmail)
            .issuer(issuer)
            .expirationTime(expires);

    if (!visaJwts.isEmpty()) {
      passportClaimSetBuilder.claim(ProviderService.GA4GH_PASSPORT_V1_CLAIM, visaJwts);
    }

    var claimsSet = passportClaimSetBuilder.build();

    var signedJWT =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(accessTokenRsaJWK.getKeyID()).build(),
            claimsSet);

    signedJWT.sign(accessTokenSigner);

    return signedJWT.serialize();
  }

  private LinkedAccount createTestLinkedAccount() {
    return LinkedAccount.builder()
        .providerId(UUID.randomUUID().toString())
        .userId(UUID.randomUUID().toString())
        .refreshToken(UUID.randomUUID().toString())
        .externalUserId(userEmail)
        .expires(passportExpiresTime)
        .build();
  }

  private GA4GHPassport createTestPassport(List<GA4GHVisa> visas) throws JOSEException {
    var visaJwts = visas.stream().map(GA4GHVisa::getJwt).collect(Collectors.toList());
    var jwtString = createPassportJwtString(passportExpires, visaJwts);
    return GA4GHPassport.builder().jwt(jwtString).expires(passportExpiresTime).build();
  }

  private String createVisaJwtString(GA4GHVisa visa) throws URISyntaxException, JOSEException {
    var visaClaimSet =
        new JWTClaimsSet.Builder()
            .expirationTime(visa.getExpires())
            .issuer(visa.getIssuer() == "null" ? null : visa.getIssuer())
            .claim(
                ProviderService.GA4GH_VISA_V1_CLAIM,
                Map.of(ProviderService.VISA_TYPE_CLAIM, visa.getVisaType()))
            .build();

    var jwtHeaderBuilder = new Builder(JWSAlgorithm.RS256);

    JWSSigner signer;
    if (visa.getTokenType() == TokenTypeEnum.document_token) {
      jwtHeaderBuilder
          .jwkURL(new URI(visa.getIssuer() + JKU_PATH))
          .keyID(documentTokenRsaJWK.getKeyID());
      signer = documentTokenSigner;
    } else {
      jwtHeaderBuilder.keyID(accessTokenRsaJWK.getKeyID());
      signer = accessTokenSigner;
    }
    var signedVisaJwt = new SignedJWT(jwtHeaderBuilder.build(), visaClaimSet);
    signedVisaJwt.sign(signer);
    return signedVisaJwt.serialize();
  }

  private GA4GHVisa createTestVisa(TokenTypeEnum tokenType)
      throws URISyntaxException, JOSEException {
    var visa =
        GA4GHVisa.builder()
            .visaType(UUID.randomUUID().toString())
            .tokenType(tokenType)
            .issuer(issuer)
            .expires(new Timestamp(passportExpires.getTime()))
            .jwt("temp")
            .build();
    visa = visa.withJwt(createVisaJwtString(visa));
    return visa;
  }
}
