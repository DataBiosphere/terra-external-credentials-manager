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
                    Provider.GITHUB.toString(),
                    TestUtils.createRandomProvider(),
                    Provider.RAS.toString(),
                    TestUtils.createRandomProvider()));
    providerOAuthClientCache = new ProviderOAuthClientCache(externalCredsConfig);
  }

  @Test
  void testGitHubBuildClientRegistration() {
    String providerName = Provider.GITHUB.toString();
    ProviderProperties providerInfo = externalCredsConfig.getProviders().get(providerName);
    String redirectUri =
        providerInfo.getAllowedRedirectUriPatterns().stream()
            .map(Pattern::toString)
            .toList()
            .get(0);
    ClientRegistration gitHubClient =
        providerOAuthClientCache.buildClientRegistration(
            providerName, externalCredsConfig.getProviders().get(providerName));

    assertEquals(
        AuthorizationGrantType.AUTHORIZATION_CODE, gitHubClient.getAuthorizationGrantType());
    assertEquals(providerInfo.getClientId(), gitHubClient.getClientId());
    assertEquals(providerInfo.getClientSecret(), gitHubClient.getClientSecret());
    assertEquals(redirectUri, gitHubClient.getRedirectUri());
  }
}
