package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ProviderTestUtil;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.generated.model.Provider;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;

class ProviderOAuthClientCacheTest extends BaseTest {

  @Autowired private ProviderOAuthClientCache providerOAuthClientCache;
  @MockBean private ExternalCredsConfig externalCredsConfig;

  @Test
  void testGitHubBuildClientRegistration() {
    Provider provider = Provider.GITHUB;
    var providerInfo = TestUtils.createRandomProvider();
    when(externalCredsConfig.getProviderProperties(provider)).thenReturn(providerInfo);

    String redirectUri =
        providerInfo.getAllowedRedirectUriPatterns().stream()
            .map(Pattern::toString)
            .toList()
            .get(0);
    ClientRegistration gitHubClient = providerOAuthClientCache.getProviderClient(provider);

    assertEquals(
        AuthorizationGrantType.AUTHORIZATION_CODE, gitHubClient.getAuthorizationGrantType());
    assertEquals(providerInfo.getClientId(), gitHubClient.getClientId());
    assertEquals(providerInfo.getClientSecret(), gitHubClient.getClientSecret());
    assertEquals(redirectUri, gitHubClient.getRedirectUri());
  }

  @Test
  void testRasBuildClientRegistration() {
    try (var mockServer = ClientAndServer.startClientAndServer()) {
      var issuerPath = "/does/not/exist";
      var url = "http://localhost:" + mockServer.getPort() + issuerPath;
      Provider provider = Provider.RAS;
      when(externalCredsConfig.getProviderProperties(provider))
          .thenReturn(TestUtils.createRandomProvider().setIssuer(url));

      //  Mock the server response
      mockServer
          .when(
              HttpRequest.request(issuerPath + "/.well-known/openid-configuration")
                  .withMethod("GET"))
          .respond(
              HttpResponse.response()
                  .withStatusCode(200)
                  .withContentType(MediaType.APPLICATION_JSON)
                  .withBody(ProviderTestUtil.wellKnownResponse(url)));

      ProviderProperties providerInfo = externalCredsConfig.getProviderProperties(provider);

      ClientRegistration rasClient = providerOAuthClientCache.getProviderClient(provider);

      assertEquals(
          AuthorizationGrantType.AUTHORIZATION_CODE, rasClient.getAuthorizationGrantType());
      assertEquals(providerInfo.getClientId(), rasClient.getClientId());
      assertEquals(providerInfo.getClientSecret(), rasClient.getClientSecret());
    }
  }
}
