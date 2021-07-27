package bio.terra.externalcreds.services;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.config.ProviderConfig.ProviderInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  @MockBean private ProviderConfig providerConfig;

  @Test
  void testDeleteLinkedAccountAndRevokeToken() {
    testWithRevokeResponseCode(HttpStatus.OK);
  }

  @Test
  void testRevokeTokenError() {
    testWithRevokeResponseCode(HttpStatus.BAD_REQUEST);
  }

  private void testWithRevokeResponseCode(HttpStatus httpStatus) {
    var revocationPath = "/test/revoke/";
    var mockServerPort = 50555;
    var linkedAccount = TestUtils.createRandomLinkedAccount();

    var providerInfo = new ProviderInfo();
    providerInfo.setClientId("clientId");
    providerInfo.setClientSecret("clientSecret");
    providerInfo.setRevokeEndpoint(
        "http://localhost:" + mockServerPort + revocationPath + "?token=%s");

    var expectedParameters =
        List.of(
            new Parameter("token", linkedAccount.getRefreshToken()),
            new Parameter("client_id", providerInfo.getClientId()),
            new Parameter("client_secret", providerInfo.getClientSecret()));

    when(providerConfig.getServices())
        .thenReturn(Map.of(linkedAccount.getProviderId(), providerInfo));

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
