package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.dataAccess.OAuth2StateDAO;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.OAuth2State;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

public class ProviderServiceTest extends BaseTest {

  @Nested
  @TestComponent
  class DeleteLink {

    @Autowired private ProviderService providerService;

    @MockBean private LinkedAccountService linkedAccountServiceMock;
    @MockBean private ExternalCredsConfig externalCredsConfigMock;

    @Test
    void testDeleteLinkedAccountAndRevokeToken() {
      testWithRevokeResponseCode(HttpStatus.OK);
    }

    @Test
    void testRevokeTokenError() {
      testWithRevokeResponseCode(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testDeleteLinkProviderNotFound() {
      when(externalCredsConfigMock.getProviders()).thenReturn(Map.of());

      assertThrows(
          NotFoundException.class,
          () ->
              providerService.deleteLink(
                  UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    }

    @Test
    void testDeleteLinkLinkNotFound() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();

      when(externalCredsConfigMock.getProviders())
          .thenReturn(Map.of(linkedAccount.getProviderName(), TestUtils.createRandomProvider()));

      when(linkedAccountServiceMock.getLinkedAccount(
              linkedAccount.getUserId(), linkedAccount.getProviderName()))
          .thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              providerService.deleteLink(
                  linkedAccount.getUserId(), linkedAccount.getProviderName()));
    }

    private void testWithRevokeResponseCode(HttpStatus httpStatus) {
      try (var mockServer = ClientAndServer.startClientAndServer()) {
        var revocationPath = "/test/revoke/";
        var linkedAccount = TestUtils.createRandomLinkedAccount();

        var providerInfo =
            TestUtils.createRandomProvider()
                .setRevokeEndpoint(
                    "http://localhost:" + mockServer.getPort() + revocationPath + "?token=%s");

        var expectedParameters =
            List.of(
                new Parameter("token", linkedAccount.getRefreshToken()),
                new Parameter("client_id", providerInfo.getClientId()),
                new Parameter("client_secret", providerInfo.getClientSecret()));

        when(externalCredsConfigMock.getProviders())
            .thenReturn(Map.of(linkedAccount.getProviderName(), providerInfo));

        when(linkedAccountServiceMock.getLinkedAccount(
                linkedAccount.getUserId(), linkedAccount.getProviderName()))
            .thenReturn(Optional.of(linkedAccount));
        when(linkedAccountServiceMock.deleteLinkedAccount(
                linkedAccount.getUserId(), linkedAccount.getProviderName()))
            .thenReturn(true);

        //  Mock the server response
        mockServer
            .when(
                HttpRequest.request(revocationPath)
                    .withMethod("POST")
                    .withQueryStringParameters(expectedParameters))
            .respond(HttpResponse.response().withStatusCode(httpStatus.value()));

        providerService.deleteLink(linkedAccount.getUserId(), linkedAccount.getProviderName());
        verify(linkedAccountServiceMock)
            .deleteLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderName());
      }
    }
  }

  @Nested
  @TestComponent
  class AuthAndRefreshPassport {

    @Autowired private ProviderService providerService;
    @Autowired private GA4GHPassportDAO passportDAO;
    @Autowired private LinkedAccountDAO linkedAccountDAO;
    @Autowired private GA4GHVisaDAO visaDAO;

    @MockBean private ExternalCredsConfig externalCredsConfigMock;
    @MockBean private ProviderClientCache providerClientCacheMock;
    @MockBean private OAuth2Service oAuth2ServiceMock;
    @MockBean private JwtUtils jwtUtilsMock;

    @Test
    void testExpiredLinkedAccountIsMarkedInvalid() {
      // save an expired linked account
      var expiredTimestamp = Timestamp.from(Instant.now().minus(Duration.ofMinutes(5)));
      var expiredLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(
              TestUtils.createRandomLinkedAccount().withExpires(expiredTimestamp));
      passportDAO.insertPassport(
          TestUtils.createRandomPassport().withLinkedAccountId(expiredLinkedAccount.getId()));

      // since the LinkedAccount itself is expired, it should be marked as invalid
      providerService.authAndRefreshPassport(expiredLinkedAccount);

      // check that the passport was deleted and the linked account was marked as invalid
      assertEmpty(
          passportDAO.getPassport(
              expiredLinkedAccount.getUserId(), expiredLinkedAccount.getProviderName()));
      var updatedLinkedAccount =
          linkedAccountDAO.getLinkedAccount(
              expiredLinkedAccount.getUserId(), expiredLinkedAccount.getProviderName());
      assertFalse(updatedLinkedAccount.get().isAuthenticated());
    }

    @Test
    void testInvalidVisaIssuer() {
      // save a non-expired linked account and nearly-expired passport
      var nonExpiredTimestamp = Timestamp.from(Instant.now().plus(Duration.ofMinutes(5)));
      var savedLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(
              TestUtils.createRandomLinkedAccount().withExpires(nonExpiredTimestamp));
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId()));
      // insert a visa with an invalid issuer
      visaDAO.insertVisa(
          TestUtils.createRandomVisa()
              .withPassportId(savedPassport.getId())
              .withIssuer("BadIssuer"));

      // mock configs
      when(externalCredsConfigMock.getProviders())
          .thenReturn(
              Map.of(
                  savedLinkedAccount.getProviderName(),
                  TestUtils.createRandomProvider().setIssuer("BadIssuer")));
      when(providerClientCacheMock.getProviderClient(savedLinkedAccount.getProviderName()))
          .thenThrow(new IllegalArgumentException());

      // check that an exception is thrown
      assertThrows(
          ExternalCredsException.class,
          () -> providerService.authAndRefreshPassport(savedLinkedAccount));
    }

    @Test
    void testUnrecoverableOAuth2Exception() {

      // save a non-expired linked account and nearly-expired passport and visa
      var nonExpiredTimestamp = Timestamp.from(Instant.now().plus(Duration.ofMinutes(5)));
      var savedLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(
              TestUtils.createRandomLinkedAccount().withExpires(nonExpiredTimestamp));
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId()));
      visaDAO.insertVisa(TestUtils.createRandomVisa().withPassportId(savedPassport.getId()));

      mockProviderConfigs(savedLinkedAccount.getProviderName());

      // mock the ClientRegistration
      var clientRegistration = createClientRegistration(savedLinkedAccount.getProviderName());
      when(providerClientCacheMock.getProviderClient(savedLinkedAccount.getProviderName()))
          .thenReturn(Optional.of(clientRegistration));

      // mock the OAuth2AuthorizationException error thrown by the Oath2Service
      when(oAuth2ServiceMock.authorizeWithRefreshToken(
              clientRegistration,
              new OAuth2RefreshToken(savedLinkedAccount.getRefreshToken(), null)))
          .thenThrow(
              new OAuth2AuthorizationException(
                  new OAuth2Error(OAuth2ErrorCodes.INSUFFICIENT_SCOPE)));

      // attempt to auth and refresh
      providerService.authAndRefreshPassport(savedLinkedAccount);

      // check that the passport was deleted and the linked account was marked as invalid
      assertEmpty(
          passportDAO.getPassport(
              savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderName()));
      var updatedLinkedAccount =
          linkedAccountDAO.getLinkedAccount(
              savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderName());
      assertFalse(updatedLinkedAccount.get().isAuthenticated());
    }

    @Test
    void testOtherOauthException() {

      // save a non-expired linked account and nearly-expired passport and visa
      var nonExpiredTimestamp = Timestamp.from(Instant.now().plus(Duration.ofMinutes(5)));
      var savedLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(
              TestUtils.createRandomLinkedAccount().withExpires(nonExpiredTimestamp));
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId()));
      visaDAO.insertVisa(TestUtils.createRandomVisa().withPassportId(savedPassport.getId()));

      mockProviderConfigs(savedLinkedAccount.getProviderName());

      // mock the ClientRegistration
      var clientRegistration = createClientRegistration(savedLinkedAccount.getProviderName());
      when(providerClientCacheMock.getProviderClient(savedLinkedAccount.getProviderName()))
          .thenReturn(Optional.of(clientRegistration));

      // mock the OAuth2AuthorizationException error thrown by the Oath2Service
      when(oAuth2ServiceMock.authorizeWithRefreshToken(
              clientRegistration,
              new OAuth2RefreshToken(savedLinkedAccount.getRefreshToken(), null)))
          .thenThrow(
              new OAuth2AuthorizationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR)));

      // check that the expected exception is thrown
      assertThrows(
          ExternalCredsException.class,
          () -> providerService.authAndRefreshPassport(savedLinkedAccount));
    }

    @Test
    void testSuccessfulAuthAndRefresh() {
      var updatedRefreshToken = "newRefreshToken";

      // save a non-expired linked account and nearly-expired passport and visa
      var nonExpiredTimestamp = Timestamp.from(Instant.now().plus(Duration.ofMinutes(5)));
      var savedLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(
              TestUtils.createRandomLinkedAccount().withExpires(nonExpiredTimestamp));
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId()));
      visaDAO.insertVisa(TestUtils.createRandomVisa().withPassportId(savedPassport.getId()));

      mockProviderConfigs(savedLinkedAccount.getProviderName());

      // mock the ClientRegistration
      var clientRegistration = createClientRegistration(savedLinkedAccount.getProviderName());
      when(providerClientCacheMock.getProviderClient(savedLinkedAccount.getProviderName()))
          .thenReturn(Optional.of(clientRegistration));

      // mock the OAuth2Authorization response
      var oAuth2TokenResponse =
          OAuth2AccessTokenResponse.withToken("tokenValue")
              .refreshToken(updatedRefreshToken)
              .tokenType(TokenType.BEARER)
              .build();
      when(oAuth2ServiceMock.authorizeWithRefreshToken(
              clientRegistration,
              new OAuth2RefreshToken(savedLinkedAccount.getRefreshToken(), null)))
          .thenReturn(oAuth2TokenResponse);

      // returning null here because it's passed to another mocked function and isn't worth mocking
      when(oAuth2ServiceMock.getUserInfo(eq(clientRegistration), Mockito.any())).thenReturn(null);

      // mock the LinkedAccountWithPassportAndVisas that would normally be read from a JWT
      var refreshedPassport =
          TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId());
      var refreshedVisa = TestUtils.createRandomVisa();
      when(jwtUtilsMock.enrichAccountWithPassportAndVisas(
              eq(savedLinkedAccount.withRefreshToken(updatedRefreshToken)), Mockito.any()))
          .thenReturn(
              new LinkedAccountWithPassportAndVisas.Builder()
                  .linkedAccount(savedLinkedAccount.withRefreshToken(updatedRefreshToken))
                  .passport(refreshedPassport)
                  .visas(List.of(refreshedVisa))
                  .build());

      // attempt to auth and refresh
      providerService.authAndRefreshPassport(savedLinkedAccount);

      // check that the passport and visa were updated in the DB
      var actualUpdatedPassport =
          passportDAO
              .getPassport(savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderName())
              .get();
      var actualUpdatedLinkedAccount =
          linkedAccountDAO.getLinkedAccount(
              savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderName());
      var actualUpdatedVisas =
          visaDAO.listVisas(savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderName());
      assertEquals(
          savedLinkedAccount.withRefreshToken(updatedRefreshToken),
          actualUpdatedLinkedAccount.get());
      assertEquals(refreshedPassport.withId(actualUpdatedPassport.getId()), actualUpdatedPassport);
      assertEquals(1, actualUpdatedVisas.size());
      assertEquals(
          refreshedVisa
              .withId(actualUpdatedVisas.get(0).getId())
              .withPassportId(actualUpdatedPassport.getId()),
          actualUpdatedVisas.get(0));
    }

    @Test
    void testMissingProviderConfigs() {
      // pass in a non-expired linked account
      var linkedAccount =
          TestUtils.createRandomLinkedAccount()
              .withExpires(
                  new Timestamp(Instant.now().plus(Duration.ofMinutes(60)).toEpochMilli()));
      // mock providerClientCache.getProviderClient to return an empty optional
      when(providerClientCacheMock.getProviderClient(linkedAccount.getProviderName()))
          .thenReturn(Optional.empty());
      // check that ExternalCredsException is thrown
      assertThrows(
          ExternalCredsException.class,
          () -> providerService.authAndRefreshPassport(linkedAccount));
    }

    private void mockProviderConfigs(String providerName) {
      when(externalCredsConfigMock.getProviders())
          .thenReturn(Map.of(providerName, TestUtils.createRandomProvider()));
    }
  }

  @Nested
  @TestComponent
  class RefreshExpiringPassports {
    @Autowired private GA4GHPassportDAO passportDAO;
    @Autowired private LinkedAccountDAO linkedAccountDAO;
    @Autowired private ProviderService providerService;

    @MockBean private ExternalCredsConfig externalCredsConfigMock;

    @Test
    void testOnlyExpiringPassportsAreRefreshed() {
      // insert two linked accounts, one with an expiring passport, one with non-expiring passport
      var ExpiringLinkedAccount = TestUtils.createRandomLinkedAccount();
      var savedExpiringLinkedAccount = linkedAccountDAO.upsertLinkedAccount(ExpiringLinkedAccount);
      var nonExpiringLinkedAccount = TestUtils.createRandomLinkedAccount();
      var savedNonExpiringLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(nonExpiringLinkedAccount);

      var expiringPassport =
          TestUtils.createRandomPassport()
              .withExpires(new Timestamp(Instant.now().toEpochMilli()))
              .withLinkedAccountId(savedExpiringLinkedAccount.getId());
      var notExpiringPassport =
          TestUtils.createRandomPassport()
              .withExpires(new Timestamp(Instant.now().plus(Duration.ofMinutes(60)).toEpochMilli()))
              .withLinkedAccountId(savedNonExpiringLinkedAccount.getId());
      passportDAO.insertPassport(expiringPassport);
      passportDAO.insertPassport(notExpiringPassport);

      // mock the configs
      when(externalCredsConfigMock.getVisaAndPassportRefreshDuration())
          .thenReturn(Duration.ofMinutes(30));

      // check that authAndRefreshPassport is called exactly once with the expiring linked account
      var providerServiceSpy = Mockito.spy(providerService);
      providerServiceSpy.refreshExpiringPassports();
      verify(providerServiceSpy).authAndRefreshPassport(any());
      verify(providerServiceSpy).authAndRefreshPassport(savedExpiringLinkedAccount);
    }
  }

  @Nested
  @TestComponent
  class ValidateVisaWithProvider {
    @Autowired private ProviderService providerService;
    @Autowired private LinkedAccountService linkedAccountService;
    @Autowired private GA4GHVisaDAO visaDAO;

    @MockBean ExternalCredsConfig externalCredsConfigMock;

    @Test
    void testSuccessfullyValidatePassportWithProvider() {
      var savedLinkedAccountWithPassportAndVisa =
          createLinkedAccountWithOldVisa(linkedAccountService);

      var visaVerificationDetails =
          getExpectedVisaVerificationDetails(savedLinkedAccountWithPassportAndVisa);

      try (var mockServer =
          mockValidationEndpointConfigsAndResponse(
              visaVerificationDetails, HttpStatus.OK, "Valid")) {

        var responseBody = providerService.validateVisaWithProvider(visaVerificationDetails);
        assertEquals(true, responseBody);

        // verify that visa last validated has been updated
        var updatedVisas =
            visaDAO.listVisas(
                savedLinkedAccountWithPassportAndVisa.getLinkedAccount().getUserId(),
                savedLinkedAccountWithPassportAndVisa.getLinkedAccount().getProviderName());
        assertEquals(savedLinkedAccountWithPassportAndVisa.getVisas().size(), updatedVisas.size());
        assertTrue(
            savedLinkedAccountWithPassportAndVisa
                .getVisas()
                .get(0)
                .getLastValidated()
                .get()
                .before(updatedVisas.get(0).getLastValidated().get()));
      }
    }

    @Test
    void testValidateInvalidVisaWithProvider() {
      var savedLinkedAccountWithPassportAndVisa =
          createLinkedAccountWithOldVisa(linkedAccountService);

      var visaVerificationDetails =
          getExpectedVisaVerificationDetails(savedLinkedAccountWithPassportAndVisa);

      try (var mockServer =
          mockValidationEndpointConfigsAndResponse(
              visaVerificationDetails, HttpStatus.BAD_REQUEST, "Invalid Passport")) {

        var responseBody = providerService.validateVisaWithProvider(visaVerificationDetails);
        assertEquals(false, responseBody);

        // verify that visa last validated has NOT been updated
        var unchangedVisas =
            visaDAO.listVisas(
                savedLinkedAccountWithPassportAndVisa.getLinkedAccount().getUserId(),
                savedLinkedAccountWithPassportAndVisa.getLinkedAccount().getProviderName());
        assertEquals(
            savedLinkedAccountWithPassportAndVisa.getVisas().size(), unchangedVisas.size());
        assertEquals(
            savedLinkedAccountWithPassportAndVisa.getVisas().get(0).getLastValidated().get(),
            unchangedVisas.get(0).getLastValidated().get());
      }
    }

    @Test
    void testProviderPropertiesIsNull() {
      var visaVerificationDetails = TestUtils.createRandomVisaVerificationDetails();
      when(externalCredsConfigMock.getProviders()).thenReturn(new HashMap<>());

      assertThrows(
          NotFoundException.class,
          () -> providerService.validateVisaWithProvider(visaVerificationDetails));
    }

    @Test
    void testNoValidationEndpoint() {
      var fakeProviderName = "fakeProvider";
      var providerProperties = TestUtils.createRandomProvider();
      var visaVerificationDetails = TestUtils.createRandomVisaVerificationDetails();

      when(externalCredsConfigMock.getProviders())
          .thenReturn(Map.of(fakeProviderName, providerProperties));

      assertThrows(
          NotFoundException.class,
          () -> providerService.validateVisaWithProvider(visaVerificationDetails));
    }

    private ClientAndServer mockValidationEndpointConfigsAndResponse(
        VisaVerificationDetails visaVerificationDetails,
        HttpStatus mockedStatusCode,
        String mockedResponseBody) {
      var validationEndpoint = "/fake-validation-endpoint";

      var mockServer = ClientAndServer.startClientAndServer();

      when(externalCredsConfigMock.getProviders())
          .thenReturn(
              Map.of(
                  visaVerificationDetails.getProviderName(),
                  TestUtils.createRandomProvider()
                      .setValidationEndpoint(
                          "http://localhost:" + mockServer.getPort() + validationEndpoint)));

      //  Mock the server response with 400 response code for invalid passport format
      var expectedQueryStringParameters =
          List.of(new Parameter("visa", visaVerificationDetails.getVisaJwt()));
      mockServer
          .when(
              HttpRequest.request(validationEndpoint)
                  .withMethod("GET")
                  .withQueryStringParameters(expectedQueryStringParameters))
          .respond(
              HttpResponse.response()
                  .withStatusCode(mockedStatusCode.value())
                  .withBody(mockedResponseBody));

      return mockServer;
    }
  }

  @Nested
  @TestComponent
  class ValidateAccessTokenVisas {
    @Autowired private ProviderService providerService;
    @Autowired private LinkedAccountService linkedAccountService;

    @Test
    void testValidResponse() {
      var providerServiceSpy = spy(providerService);
      LinkedAccountWithPassportAndVisas savedLinkedAccountWithPassportAndVisa =
          createLinkedAccountWithOldVisa(linkedAccountService);

      var expectedVisaDetails =
          getExpectedVisaVerificationDetails(savedLinkedAccountWithPassportAndVisa);
      doReturn(true).when(providerServiceSpy).validateVisaWithProvider(expectedVisaDetails);

      // check that validatePassportWithProvider is called once and no exceptions are thrown
      providerServiceSpy.validateAccessTokenVisas();
      verify(providerServiceSpy).validateVisaWithProvider(any());
      verify(providerServiceSpy).validateVisaWithProvider(expectedVisaDetails);
    }

    @Test
    void testInvalidResponse() {
      var providerServiceSpy = spy(providerService);
      LinkedAccountWithPassportAndVisas savedLinkedAccountWithPassportAndVisa =
          createLinkedAccountWithOldVisa(linkedAccountService);

      // mock the behavior of helper functions which already have their own tests
      var expectedVisaDetails =
          getExpectedVisaVerificationDetails(savedLinkedAccountWithPassportAndVisa);
      doReturn(false).when(providerServiceSpy).validateVisaWithProvider(expectedVisaDetails);
      doNothing()
          .when(providerServiceSpy)
          .authAndRefreshPassport(savedLinkedAccountWithPassportAndVisa.getLinkedAccount());

      // check that validatePassportWithProvider is called once and no exceptions are thrown
      providerServiceSpy.validateAccessTokenVisas();
      verify(providerServiceSpy).validateVisaWithProvider(any());
      verify(providerServiceSpy).validateVisaWithProvider(expectedVisaDetails);

      // check that authAndRefreshPassport was also called once
      verify(providerServiceSpy)
          .authAndRefreshPassport(savedLinkedAccountWithPassportAndVisa.getLinkedAccount());
    }
  }

  private VisaVerificationDetails getExpectedVisaVerificationDetails(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    var visa = linkedAccountWithPassportAndVisas.getVisas().get(0);
    return new VisaVerificationDetails.Builder()
        .linkedAccountId(linkedAccountWithPassportAndVisas.getLinkedAccount().getId().get())
        .visaJwt(visa.getJwt())
        .providerName(linkedAccountWithPassportAndVisas.getLinkedAccount().getProviderName())
        .visaId(visa.getId().get())
        .build();
  }

  private LinkedAccountWithPassportAndVisas createLinkedAccountWithOldVisa(
      LinkedAccountService linkedAccountService) {
    var visaNeedingVerification =
        TestUtils.createRandomVisa()
            .withTokenType(TokenTypeEnum.access_token)
            .withLastValidated(
                new Timestamp(Instant.now().minus(Duration.ofDays(50)).toEpochMilli()));

    var savedLinkedAccountWithPassportAndVisa =
        linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
            new LinkedAccountWithPassportAndVisas.Builder()
                .linkedAccount(TestUtils.createRandomLinkedAccount())
                .passport(TestUtils.createRandomPassport())
                .visas(List.of(visaNeedingVerification))
                .build());
    return savedLinkedAccountWithPassportAndVisa;
  }

  @Nested
  @TestComponent
  class ProviderAuthorizationUrl {
    @MockBean OAuth2Service oAuth2ServiceMock;
    @MockBean ProviderClientCache providerClientCacheMock;
    @MockBean ExternalCredsConfig externalCredsConfigMock;

    @Autowired ProviderService providerService;
    @Autowired OAuth2StateDAO oAuth2StateDAO;
    @Autowired ObjectMapper objectMapper;

    @Test
    void testOAuth2StatePersisted() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var clientRegistration = createClientRegistration(linkedAccount.getProviderName());
      var redirectUri = "https://foo.bar.com";
      var scopes = Set.of("email", "profile");
      ProviderProperties providerProperties = ProviderProperties.create();

      when(externalCredsConfigMock.getProviders())
          .thenReturn(Map.of(linkedAccount.getProviderName(), providerProperties));
      when(providerClientCacheMock.getProviderClient(linkedAccount.getProviderName()))
          .thenReturn(Optional.of(clientRegistration));

      // this mock captures the `state` parameter and returns it
      // we do this because the state is randomly generated and this test tests that what is
      // sent to getAuthorizationRequestUri is saved in the database
      when(oAuth2ServiceMock.getAuthorizationRequestUri(
              eq(clientRegistration),
              eq(redirectUri),
              eq(scopes),
              anyString(),
              eq(providerProperties.getAdditionalAuthorizationParameters())))
          .thenAnswer((Answer<String>) invocation -> (String) invocation.getArgument(3));

      var result =
          providerService.getProviderAuthorizationUrl(
              linkedAccount.getUserId(), linkedAccount.getProviderName(), redirectUri, scopes);
      assertPresent(result);
      // the result here should be only the state because of the mock above
      var savedState = OAuth2State.decode(objectMapper, result.get());
      assertEquals(linkedAccount.getProviderName(), savedState.getProvider());

      assertTrue(oAuth2StateDAO.deleteOidcStateIfExists(linkedAccount.getUserId(), savedState));
      // double check that the state gets removed just in case
      assertFalse(oAuth2StateDAO.deleteOidcStateIfExists(linkedAccount.getUserId(), savedState));
    }
  }

  private ClientRegistration createClientRegistration(String providerName) {
    return ClientRegistration.withRegistrationId(providerName)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .build();
  }
}
