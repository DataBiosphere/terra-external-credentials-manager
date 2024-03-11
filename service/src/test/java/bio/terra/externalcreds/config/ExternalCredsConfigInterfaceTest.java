package bio.terra.externalcreds.config;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.generated.model.Provider;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalCredsConfigInterfaceTest {

  @Test
  void testGetProviderPropertiesThrowsWhenNotFound() {
    var externalCredsConfig =
        ExternalCredsConfig.create()
            .setProviders(
                Map.of(
                    Provider.GITHUB,
                    TestUtils.createRandomProvider(),
                    Provider.RAS,
                    TestUtils.createRandomProvider()));

    assertThrows(
        NotFoundException.class, () -> externalCredsConfig.getProviderProperties(Provider.FENCE));
  }

  @Test
  void testGetProviderPropertiesReturnsProvider() {
    var providerInfo = TestUtils.createRandomProvider();
    var provider = Provider.GITHUB;
    var externalCredsConfig =
        ExternalCredsConfig.create().setProviders(Map.of(Provider.GITHUB, providerInfo));

    var actualProviderInfo = externalCredsConfig.getProviderProperties(provider);
    assertEquals(providerInfo, actualProviderInfo);
  }
}
