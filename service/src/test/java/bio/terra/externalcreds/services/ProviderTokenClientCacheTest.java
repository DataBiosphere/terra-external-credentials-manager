package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;

class ProviderTokenClientCacheTest extends BaseTest {

  @Autowired private ProviderTokenClientCache providerTokenClientCache;
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
    ClientRegistration gitHubClient = providerTokenClientCache.getProviderClient(provider);

    assertEquals(AuthorizationGrantType.REFRESH_TOKEN, gitHubClient.getAuthorizationGrantType());
    assertEquals(providerInfo.getClientId(), gitHubClient.getClientId());
    assertEquals(providerInfo.getClientSecret(), gitHubClient.getClientSecret());
    assertEquals(redirectUri, gitHubClient.getRedirectUri());
  }
}
