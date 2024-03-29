package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
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
  @MockBean ProviderOAuthClientCache providerOAuthClientCacheMock;
  @MockBean ExternalCredsConfig externalCredsConfigMock;

  @Autowired PassportProviderService passportProviderService;
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
    when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));
    var expectedPassport = jwtSigningTestUtils.createTestPassport(Collections.emptyList());
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, Collections.emptyList());
  }

  @Test
  void testAccessTokenVisa() throws URISyntaxException {
    when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));
    var visa = jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.access_token);
    var expectedPassport = jwtSigningTestUtils.createTestPassport(List.of(visa));
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, List.of(visa));
  }

  @Test
  void testDocumentTokenVisa() throws URISyntaxException {
    when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));
    var visa = jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.document_token);
    var expectedPassport = jwtSigningTestUtils.createTestPassport(List.of(visa));
    var expectedLinkedAccount = createTestLinkedAccount();
    runTest(expectedLinkedAccount, expectedPassport, List.of(visa));
  }

  @Test
  void testMultipleVisas() throws URISyntaxException {
    when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));
    var visas =
        List.of(
            jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.document_token),
            jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.access_token),
            jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.document_token),
            jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.access_token));
    var expectedPassport = jwtSigningTestUtils.createTestPassport(visas);
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
    var providerInfo = TestUtils.createRandomProvider().setScopes(scopes);
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProvider().toString())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();

    var state =
        new OAuth2State.Builder()
            .provider(linkedAccount.getProvider())
            .random(OAuth2State.generateRandomState(new SecureRandom()))
            .redirectUri(redirectUri)
            .build();
    linkedAccountService.upsertOAuth2State(linkedAccount.getUserId(), state);

    String encodedState = state.encode(objectMapper);

    when(externalCredsConfigMock.getProviderProperties(linkedAccount.getProvider()))
        .thenReturn(providerInfo);
    when(providerOAuthClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(providerClient);
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
                passportProviderService.createLink(
                    linkedAccount.getProvider(),
                    linkedAccount.getUserId(),
                    authorizationCode,
                    encodedState,
                    new AuditLogEvent.Builder().userId("userId")));

    // make sure the BadRequestException is for the right reason
    assertInstanceOf(OAuth2AuthorizationException.class, exception.getCause());
  }

  @Test
  void testNoRefreshTokenReturned() {
    var linkedAccount = createTestLinkedAccount();
    var providerInfo = TestUtils.createRandomProvider().setScopes(scopes);
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProvider().toString())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();

    var state =
        new OAuth2State.Builder()
            .provider(linkedAccount.getProvider())
            .random(OAuth2State.generateRandomState(new SecureRandom()))
            .redirectUri(redirectUri)
            .build();
    linkedAccountService.upsertOAuth2State(linkedAccount.getUserId(), state);

    String encodedState = state.encode(objectMapper);

    when(externalCredsConfigMock.getProviderProperties(linkedAccount.getProvider()))
        .thenReturn(providerInfo);
    when(providerOAuthClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(providerClient);
    var tokenResponse = mock(OAuth2AccessTokenResponse.class);
    when(tokenResponse.getRefreshToken()).thenReturn(null);
    when(oAuth2ServiceMock.authorizationCodeExchange(
            providerClient,
            authorizationCode,
            redirectUri,
            scopes,
            encodedState,
            providerInfo.getAdditionalAuthorizationParameters()))
        .thenReturn(tokenResponse);

    assertThrows(
        ExternalCredsException.class,
        () ->
            passportProviderService.createLink(
                linkedAccount.getProvider(),
                linkedAccount.getUserId(),
                authorizationCode,
                encodedState,
                new AuditLogEvent.Builder().userId("userId")));
  }

  @Test
  void testNoExternalUserIdReturned() {
    var linkedAccount = createTestLinkedAccount();
    var providerInfo = TestUtils.createRandomProvider().setScopes(scopes);
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProvider().toString())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();

    var state =
        new OAuth2State.Builder()
            .provider(linkedAccount.getProvider())
            .random(OAuth2State.generateRandomState(new SecureRandom()))
            .redirectUri(redirectUri)
            .build();
    linkedAccountService.upsertOAuth2State(linkedAccount.getUserId(), state);

    String encodedState = state.encode(objectMapper);

    when(externalCredsConfigMock.getProviderProperties(linkedAccount.getProvider()))
        .thenReturn(providerInfo);
    when(providerOAuthClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(providerClient);
    var tokenResponse =
        OAuth2AccessTokenResponse.withToken(UUID.randomUUID().toString())
            .refreshToken(linkedAccount.getRefreshToken())
            .tokenType(TokenType.BEARER)
            .build();
    when(oAuth2ServiceMock.authorizationCodeExchange(
            providerClient,
            authorizationCode,
            redirectUri,
            scopes,
            encodedState,
            providerInfo.getAdditionalAuthorizationParameters()))
        .thenReturn(tokenResponse);
    when(oAuth2ServiceMock.getUserInfo(providerClient, tokenResponse.getAccessToken()))
        .thenReturn(new DefaultOAuth2User(null, Map.of("foo", "bar"), "foo"));

    assertThrows(
        ExternalCredsException.class,
        () ->
            passportProviderService.createLink(
                linkedAccount.getProvider(),
                linkedAccount.getUserId(),
                authorizationCode,
                encodedState,
                new AuditLogEvent.Builder().userId("userId")));
  }

  private void setupMocks(
      LinkedAccount linkedAccount,
      GA4GHPassport passport,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state)
      throws URISyntaxException {
    var providerInfo = TestUtils.createRandomProvider().setScopes(scopes);
    var providerClient =
        ClientRegistration.withRegistrationId(linkedAccount.getProvider().toString())
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();
    var accessTokenResponse =
        OAuth2AccessTokenResponse.withToken(UUID.randomUUID().toString())
            .refreshToken(linkedAccount.getRefreshToken())
            .tokenType(TokenType.BEARER)
            .build();
    var userAttributes = new HashMap<String, Object>();
    userAttributes.put(providerInfo.getExternalIdClaim(), linkedAccount.getExternalUserId());
    if (passport != null) {
      userAttributes.put(JwtUtils.PASSPORT_JWT_V11_CLAIM, passport.getJwt());
    }
    OAuth2User user =
        new DefaultOAuth2User(null, userAttributes, providerInfo.getExternalIdClaim());

    when(externalCredsConfigMock.getProviderProperties(linkedAccount.getProvider()))
        .thenReturn(providerInfo);
    when(externalCredsConfigMock.getAllowedJwtIssuers())
        .thenReturn(List.of(new URI(jwtSigningTestUtils.getIssuer())));
    when(externalCredsConfigMock.getAllowedJwksUris())
        .thenReturn(
            List.of(new URI(jwtSigningTestUtils.getIssuer() + JwtSigningTestUtils.JKU_PATH)));
    when(providerOAuthClientCacheMock.getProviderClient(linkedAccount.getProvider()))
        .thenReturn(providerClient);
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
            .provider(expectedLinkedAccount.getProvider())
            .random(OAuth2State.generateRandomState(new SecureRandom()))
            .redirectUri(redirectUri)
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

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .provider(expectedLinkedAccount.getProvider())
            .userId(expectedLinkedAccount.getUserId())
            .clientIP("127.0.0.1");
    var linkedAccountWithPassportAndVisas =
        passportProviderService.createLink(
            expectedLinkedAccount.getProvider(),
            expectedLinkedAccount.getUserId(),
            authorizationCode,
            encodedState,
            auditLogEventBuilder);

    assertNotNull(linkedAccountWithPassportAndVisas);

    assertEquals(
        expectedLinkedAccount,
        linkedAccountWithPassportAndVisas
            .getLinkedAccount()
            .withExpires(jwtSigningTestUtils.passportExpiresTime)
            .withId(Optional.empty()));

    var stablePassport =
        linkedAccountWithPassportAndVisas
            .getPassport()
            .map(p -> p.withId(Optional.empty()).withLinkedAccountId(Optional.empty()));
    assertEquals(Optional.ofNullable(expectedPassport), stablePassport);

    var stableVisas =
        linkedAccountWithPassportAndVisas.getVisas().stream()
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
