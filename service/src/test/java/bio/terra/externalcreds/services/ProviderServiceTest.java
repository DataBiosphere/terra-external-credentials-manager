package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;

public class ProviderServiceTest extends BaseTest {
  @Autowired private ProviderService providerService;
  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private ExternalCredsConfig externalCredsConfig;

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
    when(externalCredsConfig.getProviders()).thenReturn(ImmutableMap.of());

    assertThrows(
        NotFoundException.class,
        () ->
            providerService.deleteLink(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
  }

  @Test
  void testDeleteLinkLinkNotFound() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();

    when(externalCredsConfig.getProviders())
        .thenReturn(
            ImmutableMap.of(linkedAccount.getProviderId(), TestUtils.createRandomProvider()));

    when(linkedAccountService.getLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId()))
        .thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> providerService.deleteLink(linkedAccount.getUserId(), linkedAccount.getProviderId()));
  }

  private void testWithRevokeResponseCode(HttpStatus httpStatus) {
    var revocationPath = "/test/revoke/";
    var mockServerPort = 50555;
    var linkedAccount = TestUtils.createRandomLinkedAccount();

    var providerInfo =
        TestUtils.createRandomProvider()
            .setRevokeEndpoint("http://localhost:" + mockServerPort + revocationPath + "?token=%s");

    var expectedParameters =
        List.of(
            new Parameter("token", linkedAccount.getRefreshToken()),
            new Parameter("client_id", providerInfo.getClientId()),
            new Parameter("client_secret", providerInfo.getClientSecret()));

    when(externalCredsConfig.getProviders())
        .thenReturn(ImmutableMap.of(linkedAccount.getProviderId(), providerInfo));

    when(linkedAccountService.getLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId()))
        .thenReturn(Optional.of(linkedAccount));
    when(linkedAccountService.deleteLinkedAccount(
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
      verify(linkedAccountService)
          .deleteLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    } finally {
      mockServer.stop();
    }
  }
}
