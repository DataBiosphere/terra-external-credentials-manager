package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class AuthorizationCodeExchangeTest extends BaseTest {
  @MockBean OAuth2Service oAuth2ServiceMock;
  @MockBean ProviderClientCache providerClientCacheMock;
  @MockBean ExternalCredsConfig externalCredsConfigMock;

  @Autowired ProviderService providerService;
  @Autowired PassportService passportService;
  @Autowired JwtUtils jwtUtils;

  private static JwtSigningTestUtils jwtSigningTestUtils = new JwtSigningTestUtils();

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
    jwtSigningTestUtils.setUpJwtVerification();
  }

  @AfterAll
  static void tearDown() {
    jwtSigningTestUtils.tearDown();
  }

  @Test
  void testNoVisas() throws JOSEException, URISyntaxException {
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
  void testNoPassport() throws URISyntaxException {
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, null, Collections.emptyList());
  }

  private void setupMocks(
      LinkedAccount linkedAccount,
      GA4GHPassport passport,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state)
      throws URISyntaxException {
    var providerInfo = TestUtils.createRandomProvider();
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProviderName())
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
      userAttributes.put(JwtUtils.PASSPORT_JWT_V11_CLAIM, passport.getJwt());
    }
    OAuth2User user =
        new DefaultOAuth2User(null, userAttributes, ProviderService.EXTERNAL_USERID_ATTR);

    when(externalCredsConfigMock.getProviders())
        .thenReturn(Map.of(linkedAccount.getProviderName(), providerInfo));
    when(externalCredsConfigMock.getAllowedJwksUris())
        .thenReturn(
            List.of(new URI(jwtSigningTestUtils.getIssuer() + JwtSigningTestUtils.JKU_PATH)));
    when(providerClientCacheMock.getProviderClient(linkedAccount.getProviderName()))
        .thenReturn(Optional.of(providerClient));
    when(oAuth2ServiceMock.authorizationCodeExchange(
            providerClient,
            authorizationCode,
            redirectUri,
            scopes,
            state,
            providerInfo.getAdditionalAuthorizationParameters()))
        .thenReturn(accessTokenResponse);
    when(oAuth2ServiceMock.getUserInfo(providerClient, accessTokenResponse.getAccessToken()))
        .thenReturn(user);
  }

  private void runTest(
      LinkedAccount expectedLinkedAccount,
      GA4GHPassport expectedPassport,
      List<GA4GHVisa> expectedVisas)
      throws URISyntaxException {

    setupMocks(
        expectedLinkedAccount, expectedPassport, authorizationCode, redirectUri, scopes, state);

    var linkedAccountWithPassportAndVisas =
        providerService.createLink(
            expectedLinkedAccount.getProviderName(),
            expectedLinkedAccount.getUserId(),
            authorizationCode,
            redirectUri,
            scopes,
            state);

    assertPresent(linkedAccountWithPassportAndVisas);

    assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas
            .get()
            .getLinkedAccount()
            .withExpires(passportExpiresTime)
            .withId(Optional.empty()));

    var stablePassport =
        linkedAccountWithPassportAndVisas
            .get()
            .getPassport()
            .map(p -> p.withId(Optional.empty()).withLinkedAccountId(Optional.empty()));
    assertEquals(Optional.ofNullable(expectedPassport), stablePassport);

    var stableVisas =
        linkedAccountWithPassportAndVisas.get().getVisas().stream()
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
            .issuer(jwtSigningTestUtils.getIssuer())
            .expirationTime(expires);

    if (!visaJwts.isEmpty()) {
      passportClaimSetBuilder.claim(JwtUtils.GA4GH_PASSPORT_V1_CLAIM, visaJwts);
    }

    var claimsSet = passportClaimSetBuilder.build();
    return jwtSigningTestUtils.createSignedJwt(claimsSet);
  }

  private LinkedAccount createTestLinkedAccount() {
    return TestUtils.createRandomLinkedAccount()
        .withExternalUserId(userEmail)
        .withExpires(passportExpiresTime);
  }

  private GA4GHPassport createTestPassport(List<GA4GHVisa> visas) throws JOSEException {
    var visaJwts = visas.stream().map(GA4GHVisa::getJwt).collect(Collectors.toList());
    var jwtString = createPassportJwtString(passportExpires, visaJwts);
    return new GA4GHPassport.Builder().jwt(jwtString).expires(passportExpiresTime).build();
  }

  private GA4GHVisa createTestVisa(TokenTypeEnum tokenType)
      throws URISyntaxException, JOSEException {
    return jwtSigningTestUtils.createTestVisaWithJwt(tokenType, passportExpiresTime);
  }
}
