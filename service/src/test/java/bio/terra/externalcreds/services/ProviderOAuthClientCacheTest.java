package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.generated.model.Provider;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;

class ProviderOAuthClientCacheTest extends BaseTest {

  private ProviderOAuthClientCache providerOAuthClientCache;
  private ExternalCredsConfig externalCredsConfig;

  @BeforeEach
  void setUp() {
    externalCredsConfig =
        ExternalCredsConfig.create()
            .setProviders(
                Map.of(
                    Provider.GITHUB,
                    TestUtils.createRandomProvider(),
                    Provider.RAS,
                    TestUtils.createRandomProvider()));
    providerOAuthClientCache = new ProviderOAuthClientCache(externalCredsConfig);
  }

  @Test
  void testGitHubBuildClientRegistration() {
    Provider provider = Provider.GITHUB;
    ProviderProperties providerInfo = externalCredsConfig.getProviderProperties(provider);
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
}
