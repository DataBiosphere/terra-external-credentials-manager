package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.web.client.HttpClientErrorException;

public class ProviderServiceTest extends BaseTest {

  @Nested
  @TestComponent
  class DeleteLink {

    @Autowired private ProviderService providerService;

    @MockBean private LinkedAccountService mockLinkedAccountService;
    @MockBean private ExternalCredsConfig mockExternalCredsConfig;

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
      when(mockExternalCredsConfig.getProviders()).thenReturn(Map.of());

      assertThrows(
          NotFoundException.class,
          () ->
              providerService.deleteLink(
                  UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    }

    @Test
    void testDeleteLinkLinkNotFound() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();

      when(mockExternalCredsConfig.getProviders())
          .thenReturn(Map.of(linkedAccount.getProviderId(), TestUtils.createRandomProvider()));

      when(mockLinkedAccountService.getLinkedAccount(
              linkedAccount.getUserId(), linkedAccount.getProviderId()))
          .thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              providerService.deleteLink(linkedAccount.getUserId(), linkedAccount.getProviderId()));
    }

    private void testWithRevokeResponseCode(HttpStatus httpStatus) {
      var revocationPath = "/test/revoke/";
      var mockServerPort = 50555;
      var linkedAccount = TestUtils.createRandomLinkedAccount();

      var providerInfo =
          TestUtils.createRandomProvider()
              .setRevokeEndpoint(
                  "http://localhost:" + mockServerPort + revocationPath + "?token=%s");

      var expectedParameters =
          List.of(
              new Parameter("token", linkedAccount.getRefreshToken()),
              new Parameter("client_id", providerInfo.getClientId()),
              new Parameter("client_secret", providerInfo.getClientSecret()));

      when(mockExternalCredsConfig.getProviders())
          .thenReturn(Map.of(linkedAccount.getProviderId(), providerInfo));

      when(mockLinkedAccountService.getLinkedAccount(
              linkedAccount.getUserId(), linkedAccount.getProviderId()))
          .thenReturn(Optional.of(linkedAccount));
      when(mockLinkedAccountService.deleteLinkedAccount(
              linkedAccount.getUserId(), linkedAccount.getProviderId()))
          .thenReturn(true);

      //  Mock the server response
      var mockServer = ClientAndServer.startClientAndServer(mockServerPort);
      try {
        mockServer
            .when(
                HttpRequest.request(revocationPath)
                    .withMethod("POST")
                    .withQueryStringParameters(expectedParameters))
            .respond(HttpResponse.response().withStatusCode(httpStatus.value()));

        providerService.deleteLink(linkedAccount.getUserId(), linkedAccount.getProviderId());
        verify(mockLinkedAccountService)
            .deleteLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
      } finally {
        mockServer.stop();
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

    @MockBean private ExternalCredsConfig mockExternalCredsConfig;
    @MockBean private ProviderClientCache mockProviderClientCache;
    @MockBean private OAuth2Service mockOAuth2Service;
    @MockBean private JwtUtils jwtUtils;

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
              expiredLinkedAccount.getUserId(), expiredLinkedAccount.getProviderId()));
      var updatedLinkedAccount =
          linkedAccountDAO.getLinkedAccount(
              expiredLinkedAccount.getUserId(), expiredLinkedAccount.getProviderId());
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
      when(mockExternalCredsConfig.getProviders())
          .thenReturn(
              Map.of(
                  savedLinkedAccount.getProviderId(),
                  TestUtils.createRandomProvider().setIssuer("BadIssuer")));
      when(mockProviderClientCache.getProviderClient(savedLinkedAccount.getProviderId()))
          .thenThrow(new IllegalArgumentException());

      // check that an exception is thrown
      assertThrows(
          ExternalCredsException.class,
          () -> providerService.authAndRefreshPassport(savedLinkedAccount));
    }

    @Test
    void testOathBadRequestException() {

      // save a non-expired linked account and nearly-expired passport and visa
      var nonExpiredTimestamp = Timestamp.from(Instant.now().plus(Duration.ofMinutes(5)));
      var savedLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(
              TestUtils.createRandomLinkedAccount().withExpires(nonExpiredTimestamp));
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId()));
      visaDAO.insertVisa(TestUtils.createRandomVisa().withPassportId(savedPassport.getId()));

      mockProviderConfigs(savedLinkedAccount.getProviderId());

      // mock the ClientRegistration
      var clientRegistration = createClientRegistration(savedLinkedAccount.getProviderId());
      when(mockProviderClientCache.getProviderClient(savedLinkedAccount.getProviderId()))
          .thenReturn(Optional.of(clientRegistration));

      // mock the OAuth2AuthorizationException error thrown by the Oath2Service
      when(mockOAuth2Service.authorizeWithRefreshToken(
              clientRegistration,
              new OAuth2RefreshToken(savedLinkedAccount.getRefreshToken(), null)))
          .thenThrow(
              new OAuth2AuthorizationException(
                  new OAuth2Error(HttpStatus.BAD_REQUEST.toString()),
                  new HttpClientErrorException(HttpStatus.BAD_REQUEST)));

      // attempt to auth and refresh
      providerService.authAndRefreshPassport(savedLinkedAccount);

      // check that the passport was deleted and the linked account was marked as invalid
      assertEmpty(
          passportDAO.getPassport(
              savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderId()));
      var updatedLinkedAccount =
          linkedAccountDAO.getLinkedAccount(
              savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderId());
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

      mockProviderConfigs(savedLinkedAccount.getProviderId());

      // mock the ClientRegistration
      var clientRegistration = createClientRegistration(savedLinkedAccount.getProviderId());
      when(mockProviderClientCache.getProviderClient(savedLinkedAccount.getProviderId()))
          .thenReturn(Optional.of(clientRegistration));

      // mock the OAuth2AuthorizationException error thrown by the Oath2Service
      when(mockOAuth2Service.authorizeWithRefreshToken(
              clientRegistration,
              new OAuth2RefreshToken(savedLinkedAccount.getRefreshToken(), null)))
          .thenThrow(
              new OAuth2AuthorizationException(new OAuth2Error(HttpStatus.BAD_REQUEST.toString())));

      // check that the expected exception is thrown
      assertThrows(
          ExternalCredsException.class,
          () -> providerService.authAndRefreshPassport(savedLinkedAccount));
    }

    @Test
    void testSuccessfulAuthAndRefresh() {
      // save a non-expired linked account and nearly-expired passport and visa
      var nonExpiredTimestamp = Timestamp.from(Instant.now().plus(Duration.ofMinutes(5)));
      var savedLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(
              TestUtils.createRandomLinkedAccount().withExpires(nonExpiredTimestamp));
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId()));
      visaDAO.insertVisa(TestUtils.createRandomVisa().withPassportId(savedPassport.getId()));

      mockProviderConfigs(savedLinkedAccount.getProviderId());

      // mock the ClientRegistration
      var clientRegistration = createClientRegistration(savedLinkedAccount.getProviderId());
      when(mockProviderClientCache.getProviderClient(savedLinkedAccount.getProviderId()))
          .thenReturn(Optional.of(clientRegistration));

      // mock the OAuth2Authorization response
      var oAuth2TokenResponse =
          OAuth2AccessTokenResponse.withToken("tokenValue")
              .refreshToken(savedLinkedAccount.getRefreshToken())
              .tokenType(TokenType.BEARER)
              .build();
      when(mockOAuth2Service.authorizeWithRefreshToken(
              clientRegistration,
              new OAuth2RefreshToken(savedLinkedAccount.getRefreshToken(), null)))
          .thenReturn(oAuth2TokenResponse);

      // returning null here because it's passed to another mocked function and isn't worth mocking
      when(mockOAuth2Service.getUserInfo(eq(clientRegistration), Mockito.any())).thenReturn(null);

      // mock the LinkedAccountWithPassportAndVisas that would normally be read from a JWT
      var refreshedPassport =
          TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId());
      var refreshedVisa = TestUtils.createRandomVisa();
      when(jwtUtils.enrichAccountWithPassportAndVisas(eq(savedLinkedAccount), Mockito.any()))
          .thenReturn(
              new LinkedAccountWithPassportAndVisas.Builder()
                  .linkedAccount(savedLinkedAccount)
                  .passport(refreshedPassport)
                  .visas(List.of(refreshedVisa))
                  .build());

      // attempt to auth and refresh
      providerService.authAndRefreshPassport(savedLinkedAccount);

      // check that the passport and visa were updated in the DB
      var actualUpdatedPassport =
          passportDAO
              .getPassport(savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderId())
              .get();
      var actualUpdatedVisas = visaDAO.listVisas(actualUpdatedPassport.getId().get());
      assertEquals(refreshedPassport.withId(actualUpdatedPassport.getId()), actualUpdatedPassport);
      assertEquals(1, actualUpdatedVisas.size());
      assertEquals(
          refreshedVisa
              .withId(actualUpdatedVisas.get(0).getId())
              .withPassportId(actualUpdatedPassport.getId()),
          actualUpdatedVisas.get(0));
    }

    private void mockProviderConfigs(String providerId) {
      when(mockExternalCredsConfig.getProviders())
          .thenReturn(Map.of(providerId, TestUtils.createRandomProvider()));
    }

    private ClientRegistration createClientRegistration(String providerId) {
      return ClientRegistration.withRegistrationId(providerId)
          .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
          .build();
    }
  }

  @Nested
  @TestComponent
  class CheckAndRefreshPassportsAndVisas {

    @Autowired private GA4GHPassportDAO passportDAO;
    @Autowired private LinkedAccountDAO linkedAccountDAO;

    @Test
    void testExpiredPassportsAreRefreshed() {
      // TODO: make sure authAndRefreshPassport is called once
      var expiredLinkedAccount = TestUtils.createRandomLinkedAccount();
      var savedExpiredLinkedAccount = linkedAccountDAO.upsertLinkedAccount(expiredLinkedAccount);
      var nonExpiredLinkedAccount = TestUtils.createRandomLinkedAccount();
      var savedNonExpiredLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(nonExpiredLinkedAccount);

      var expiredPassport =
          TestUtils.createRandomPassport()
              .withExpires(new Timestamp(Instant.now().toEpochMilli()))
              .withLinkedAccountId(savedExpiredLinkedAccount.getId());
      var notExpiredPassport =
          TestUtils.createRandomPassport()
              .withExpires(new Timestamp(Instant.now().plus(Duration.ofMinutes(20)).toEpochMilli()))
              .withLinkedAccountId(savedNonExpiredLinkedAccount.getId());
      passportDAO.insertPassport(expiredPassport);
      passportDAO.insertPassport(notExpiredPassport);

      // assertEquals(0, failedRefreshAccountCount);
    }
  }
}
