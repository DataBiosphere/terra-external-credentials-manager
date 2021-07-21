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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

public class AuthorizationCodeExchangeTest extends BaseTest {

  @MockBean OAuth2Service oAuth2Service;
  @MockBean ProviderClientCache providerClientCache;
  @MockBean ProviderConfig providerConfig;

  @Autowired ProviderService providerService;

  public MockWebServer mockBackEnd;

  private RSAKey rsaJWK;
  private JWKSet jwkSet;
  private JWSSigner signer;
  private String issuer;

  private final String authorizationCode = UUID.randomUUID().toString();
  private final String redirectUri = "https://test/redirect/uri";
  private final Set<String> scopes = Set.of("email", "ga4gh");
  private final String state = UUID.randomUUID().toString();
  private final String userEmail = "test@user.com";
  // Round the expiration to the nearest second because it will be rounded in the JWT.
  private final Date passportExpires = new Date((new Date().getTime() + 60 * 1000) / 1000 * 1000);

  @BeforeEach
  void setUp() throws IOException, JOSEException {
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();

    rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
    jwkSet = new JWKSet(rsaJWK);

    // Create RSA-signer with the private key
    signer = new RSASSASigner(rsaJWK);
    issuer = "http://localhost:" + mockBackEnd.getPort();
  }

  @AfterEach
  void tearDown() throws IOException {
    mockBackEnd.shutdown();
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
    runTest(expectedLinkedAccount, null, null);
  }

  private void runTest(
      LinkedAccount expectedLinkedAccount,
      GA4GHPassport expectedPassport,
      List<GA4GHVisa> expectedVisas) {
    int jwtCount =
        expectedPassport == null
            ? 0
            : 1 + Objects.requireNonNullElse(expectedVisas, Collections.emptyList()).size();
    mockEverything(
        expectedLinkedAccount,
        expectedPassport,
        authorizationCode,
        redirectUri,
        scopes,
        state,
        issuer,
        jwtCount);

    var linkedAccountWithPassportAndVisas =
        providerService.useAuthorizationCodeToGetLinkedAccount(
            expectedLinkedAccount.getProviderId(),
            expectedLinkedAccount.getUserId(),
            authorizationCode,
            redirectUri,
            scopes,
            state);

    Assertions.assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas.getLinkedAccount().withExpires(null));
    Assertions.assertEquals(expectedPassport, linkedAccountWithPassportAndVisas.getPassport());
    List<GA4GHVisa> visasWithoutLastValidated =
        linkedAccountWithPassportAndVisas.getVisas() == null
            ? null
            : linkedAccountWithPassportAndVisas.getVisas().stream()
                .map(visa -> visa.withLastValidated(null))
                .collect(Collectors.toList());
    Assertions.assertEquals(expectedVisas, visasWithoutLastValidated);
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
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProviderId())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();
    var accessTokenResponse =
        OAuth2AccessTokenResponse.withToken(accessToken)
            .refreshToken(linkedAccount.getRefreshToken())
            .tokenType(TokenType.BEARER)
            .build();
    Map<String, Object> userAttributes = new HashMap<>();
    userAttributes.put(ProviderService.EXTERNAL_USERID_ATTR, linkedAccount.getExternalUserId());
    if (passport != null) {
      userAttributes.put(ProviderService.PASSPORT_JWT_V11_CLAIM, passport.getJwt());
    }
    OAuth2User user =
        new DefaultOAuth2User(null, userAttributes, ProviderService.EXTERNAL_USERID_ATTR);

    when(providerConfig.getServices())
        .thenReturn(Map.of(linkedAccount.getProviderId(), providerInfo));
    when(providerClientCache.getProviderClient(linkedAccount.getProviderId()))
        .thenReturn(providerClient);
    when(oAuth2Service.authorizationCodeExchange(
            providerClient, authorizationCode, redirectUri, scopes, state, null))
        .thenReturn(accessTokenResponse);
    when(oAuth2Service.getUserInfo(providerClient, accessTokenResponse.getAccessToken()))
        .thenReturn(user);

    var wellKnownConfigMap =
        Map.of(
            "issuer",
            issuer,
            "jwks_uri",
            String.format("http://localhost:%d/openid/connect/jwks.json", mockBackEnd.getPort()));

    for (int i = 0; i < jwtCount; i++) {
      enqueueResponsesForJwtValidation(wellKnownConfigMap);
    }
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

  private String createPassportJwtString(Date expires, List<String> visaJwts) throws JOSEException {

    JWTClaimsSet.Builder passportClaimSetBuilder =
        new JWTClaimsSet.Builder()
            .subject(UUID.randomUUID().toString())
            .claim(ProviderService.EXTERNAL_USERID_ATTR, userEmail)
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

  private LinkedAccount createTestLinkedAccount() {
    return LinkedAccount.builder()
        .providerId(UUID.randomUUID().toString())
        .userId(UUID.randomUUID().toString())
        .refreshToken(UUID.randomUUID().toString())
        .externalUserId(userEmail)
        .build();
  }

  private GA4GHPassport createTestPassport(List<GA4GHVisa> visas) throws JOSEException {
    var visaJwts = visas.stream().map(GA4GHVisa::getJwt);
    String jwtString =
        createPassportJwtString(passportExpires, visaJwts.collect(Collectors.toList()));
    var expectedPassport =
        GA4GHPassport.builder()
            .jwt(jwtString)
            .expires(new Timestamp(passportExpires.getTime()))
            .build();
    return expectedPassport;
  }

  private GA4GHVisa createTestVisa(TokenTypeEnum tokenType)
      throws URISyntaxException, JOSEException {
    var visa =
        GA4GHVisa.builder()
            .visaType("")
            .tokenType(tokenType)
            .issuer(issuer)
            .expires(new Timestamp(passportExpires.getTime()))
            .build();
    visa = visa.withJwt(createVisaJwtString(visa));
    return visa;
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
