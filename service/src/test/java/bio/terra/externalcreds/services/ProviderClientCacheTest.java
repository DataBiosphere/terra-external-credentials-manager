package bio.terra.externalcreds.services;

import static org.assertj.core.api.Assertions.assertThat;

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

public class ProviderClientCacheTest extends BaseTest {

  private ProviderClientCache providerClientCache;
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
    providerClientCache = new ProviderClientCache(externalCredsConfig);
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
        providerClientCache.buildClientRegistration(
            providerName, externalCredsConfig.getProviders().get(providerName));
    ClientRegistration expectedClient =
        ClientRegistration.withRegistrationId(providerName)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .clientId(providerInfo.getClientId())
            .clientSecret(providerInfo.getClientSecret())
            .issuerUri(providerInfo.getIssuer())
            .redirectUri(redirectUri)
            .userNameAttributeName(providerInfo.getUserNameAttributeName())
            .userInfoUri(providerInfo.getUserInfoEndpoint().get())
            .authorizationUri(providerInfo.getAuthorizationEndpoint().get())
            .tokenUri(providerInfo.getTokenEndpoint().get())
            .build();
    assertThat(gitHubClient.equals(expectedClient));
  }
}
