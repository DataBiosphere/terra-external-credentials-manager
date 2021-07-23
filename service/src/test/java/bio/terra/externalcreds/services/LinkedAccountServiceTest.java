package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.config.ProviderConfig.ProviderInfo;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.model.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

public class LinkedAccountServiceTest extends BaseTest {

  @MockBean ProviderConfig providerConfig;

  @Autowired private LinkedAccountService linkedAccountService;
  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  @Test
  @Transactional
  @Rollback
  void testGetLinkedAccount() {
    var linkedAccount = createRandomLinkedAccount();
    linkedAccountDAO.upsertLinkedAccount(linkedAccount);

    var savedLinkedAccount =
        linkedAccountService.getLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(linkedAccount, savedLinkedAccount.withId(0));
  }

  @Test
  @Transactional
  @Rollback
  void testSaveLinkedAccountWithPassportAndVisas() {
    var linkedAccount = createRandomLinkedAccount();
    var passport = createRandomPassport();
    var visas = List.of(createRandomVisa(), createRandomVisa(), createRandomVisa());
    var savedLinkedAccount1 = saveAndValidateLinkedAccount(linkedAccount, passport, visas);

    // save again with new passport and visas to test overwrite
    var passport2 = createRandomPassport();
    var visas2 = List.of(createRandomVisa(), createRandomVisa(), createRandomVisa());
    var savedLinkedAccount2 = saveAndValidateLinkedAccount(linkedAccount, passport2, visas2);

    // saved linked accounts should the same
    assertEquals(savedLinkedAccount1, savedLinkedAccount2);
  }

  @Test
  @Transactional
  @Rollback
  void testSaveLinkedAccountWithPassportNoVisas() {
    var linkedAccount = createRandomLinkedAccount();
    var passport = createRandomPassport();
    saveAndValidateLinkedAccount(linkedAccount, passport, Collections.emptyList());
  }

  @Test
  @Transactional
  @Rollback
  void testSaveLinkedAccountWithoutPassport() {
    var linkedAccount = createRandomLinkedAccount();
    saveAndValidateLinkedAccount(linkedAccount, null, Collections.emptyList());
  }

  @Test
  @Transactional
  @Rollback
  void testRevokeRefreshToken() {
    var providerId = "provider";
    var refreshToken = "refreshToken";
    var path = "/test/revoke/";
    var mockServerPort = 50555;

    var providerInfo = new ProviderInfo();
    providerInfo.setClientId("clientId");
    providerInfo.setClientSecret("clientSecret");
    providerInfo.setRevokeEndpoint("http://localhost:" + mockServerPort + path + "?token=%s");

    var expectedParameters =
        List.of(
            new Parameter("token", refreshToken),
            new Parameter("client_id", providerInfo.getClientId()),
            new Parameter("client_secret", providerInfo.getClientSecret()));

    when(providerConfig.getServices()).thenReturn(Map.of(providerId, providerInfo));

    //  Mock the server response
    var mockServer = ClientAndServer.startClientAndServer(mockServerPort);
    mockServer
        .when(
            HttpRequest.request(path)
                .withMethod("POST")
                .withQueryStringParameters(expectedParameters))
        .respond(
            HttpResponse.response("{ \"result\": \"revoked\" }")
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON));

    // Test that the post request is formatted correctly and no errors are thrown
    linkedAccountService.revokeRefreshToken(providerId, refreshToken);

    mockServer.stop();
  }

  // TODO test that revokeProviderLink throws an exception when it gets a non 200 response

  // TODO test deleteLinkedAccountAndRevokeToken

  private LinkedAccount saveAndValidateLinkedAccount(
      LinkedAccount linkedAccount, GA4GHPassport passport, List<GA4GHVisa> visas) {
    var savedLinkedAccount =
        linkedAccountService.saveLinkedAccount(
            LinkedAccountWithPassportAndVisas.builder()
                .linkedAccount(linkedAccount)
                .passport(passport)
                .visas(visas)
                .build());

    assertEquals(linkedAccount, savedLinkedAccount.withId(0));
    assertTrue(savedLinkedAccount.getId() > 0);

    var savedPassport = passportDAO.getPassport(savedLinkedAccount.getId());
    if (passport == null) {
      assertNull(savedPassport);
    } else {
      assertEquals(passport, savedPassport.withId(0).withLinkedAccountId(0));
      var savedVisas = visaDAO.listVisas(savedPassport.getId());
      assertEquals(
          visas,
          savedVisas.stream().map(v -> v.withId(0).withPassportId(0)).collect(Collectors.toList()));
    }

    return savedLinkedAccount;
  }

  private Timestamp getRandomTimestamp() {
    return new Timestamp(System.currentTimeMillis());
  }

  private LinkedAccount createRandomLinkedAccount() {
    return LinkedAccount.builder()
        .expires(getRandomTimestamp())
        .providerId(UUID.randomUUID().toString())
        .refreshToken(UUID.randomUUID().toString())
        .userId(UUID.randomUUID().toString())
        .externalUserId(UUID.randomUUID().toString())
        .build();
  }

  private GA4GHPassport createRandomPassport() {
    return GA4GHPassport.builder()
        .jwt(UUID.randomUUID().toString())
        .expires(getRandomTimestamp())
        .build();
  }

  private GA4GHVisa createRandomVisa() {
    return GA4GHVisa.builder()
        .visaType(UUID.randomUUID().toString())
        .tokenType(TokenTypeEnum.access_token)
        .expires(getRandomTimestamp())
        .issuer(UUID.randomUUID().toString())
        .jwt(UUID.randomUUID().toString())
        .build();
  }
}
