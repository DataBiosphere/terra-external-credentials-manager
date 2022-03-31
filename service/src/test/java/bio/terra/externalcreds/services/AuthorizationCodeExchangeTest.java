package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.OAuth2State;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class AuthorizationCodeExchangeTest extends BaseTest {
  @MockBean OAuth2Service oAuth2ServiceMock;
  @MockBean ProviderClientCache providerClientCacheMock;
  @MockBean ExternalCredsConfig externalCredsConfigMock;

  @Autowired ProviderService providerService;
  @Autowired PassportService passportService;
  @Autowired LinkedAccountService linkedAccountService;
  @Autowired JwtUtils jwtUtils;
  @Autowired ObjectMapper objectMapper;

  private static final JwtSigningTestUtils jwtSigningTestUtils = new JwtSigningTestUtils();

  private final String authorizationCode = UUID.randomUUID().toString();
  private final String redirectUri = "https://test/redirect/uri";
  private final Set<String> scopes = Set.of("email", "ga4gh");
  private final String userEmail = "test@user.com";

  @BeforeAll
  static void setUpJwtVerification() throws JOSEException {
    jwtSigningTestUtils.setUpJwtVerification();
  }

  @AfterAll
  static void tearDown() {
    jwtSigningTestUtils.tearDown();
  }

  @Test
  void testNoVisas() throws URISyntaxException {
    var expectedPassport =
        jwtSigningTestUtils.createTestPassport(Collections.emptyList(), userEmail);
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, Collections.emptyList());
  }

  @Test
  void testAccessTokenVisa() throws URISyntaxException {
    var visa = jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.access_token);
    var expectedPassport = jwtSigningTestUtils.createTestPassport(List.of(visa), userEmail);
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, List.of(visa));
  }

  @Test
  void testDocumentTokenVisa() throws URISyntaxException {
    var visa = jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.document_token);
    var expectedPassport = jwtSigningTestUtils.createTestPassport(List.of(visa), userEmail);
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, List.of(visa));
  }

  @Test
  void testMultipleVisas() throws URISyntaxException {
    var visas =
        List.of(
            jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.document_token),
            jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.access_token),
            jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.document_token),
            jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.access_token));
    var expectedPassport = jwtSigningTestUtils.createTestPassport(visas, userEmail);
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, visas);
  }

  @Test
  void testNoPassport() throws URISyntaxException {
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, null, Collections.emptyList());
  }

  @Test
  void testInvalidAuthorizationCode() {
    var linkedAccount = createTestLinkedAccount();
    var providerInfo = TestUtils.createRandomProvider();
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProviderName())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();

    var state =
        new OAuth2State.Builder()
            .provider(linkedAccount.getProviderName())
            .random(OAuth2State.generateRandomState(new SecureRandom()))
            .build();
    linkedAccountService.upsertOAuth2State(linkedAccount.getUserId(), state);

    String encodedState = state.encode(objectMapper);

    when(externalCredsConfigMock.getProviders())
        .thenReturn(Map.of(linkedAccount.getProviderName(), providerInfo));
    when(providerClientCacheMock.getProviderClient(linkedAccount.getProviderName()))
        .thenReturn(Optional.of(providerClient));
    when(oAuth2ServiceMock.authorizationCodeExchange(
            providerClient,
            authorizationCode,
            redirectUri,
            scopes,
            encodedState,
            providerInfo.getAdditionalAuthorizationParameters()))
        .thenThrow(new OAuth2AuthorizationException(new OAuth2Error("bad code")));

    var exception =
        assertThrows(
            BadRequestException.class,
            () ->
                providerService.createLink(
                    linkedAccount.getProviderName(),
                    linkedAccount.getUserId(),
                    authorizationCode,
                    redirectUri,
                    scopes,
                    encodedState));

    // make sure the BadRequestException is for the right reason
    assertInstanceOf(OAuth2AuthorizationException.class, exception.getCause());
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
    when(externalCredsConfigMock.getAllowedJwtIssuers())
        .thenReturn(List.of(new URI(jwtSigningTestUtils.getIssuer())));
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

    var state =
        new OAuth2State.Builder()
            .provider(expectedLinkedAccount.getProviderName())
            .random(OAuth2State.generateRandomState(new SecureRandom()))
            .build();

    String encodedState = state.encode(objectMapper);
    setupMocks(
        expectedLinkedAccount,
        expectedPassport,
        authorizationCode,
        redirectUri,
        scopes,
        encodedState);

    linkedAccountService.upsertOAuth2State(expectedLinkedAccount.getUserId(), state);

    var linkedAccountWithPassportAndVisas =
        providerService.createLink(
            expectedLinkedAccount.getProviderName(),
            expectedLinkedAccount.getUserId(),
            authorizationCode,
            redirectUri,
            scopes,
            encodedState);

    assertPresent(linkedAccountWithPassportAndVisas);

    assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas
            .get()
            .getLinkedAccount()
            .withExpires(jwtSigningTestUtils.passportExpiresTime)
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
            .toList();
    assertEquals(expectedVisas, stableVisas);

    // state should have been removed from the db
    assertThrows(
        BadRequestException.class,
        () ->
            linkedAccountService.validateAndDeleteOAuth2State(
                expectedLinkedAccount.getUserId(), state));
  }

  private LinkedAccount createTestLinkedAccount() {
    return TestUtils.createRandomLinkedAccount()
        .withExternalUserId(userEmail)
        .withExpires(jwtSigningTestUtils.passportExpiresTime);
  }
}
