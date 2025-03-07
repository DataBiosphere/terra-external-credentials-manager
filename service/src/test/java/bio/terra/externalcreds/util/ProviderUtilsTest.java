package bio.terra.externalcreds.util;

import static bio.terra.externalcreds.services.JwtUtils.GA4GH_PASSPORT_V1_CLAIM;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ProviderProperties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderUtilsTest extends BaseTest {

  @Test
  void testPassportProvider() {
    var providerProperties =
        ProviderProperties.create()
            .setScopes(Set.of("openid", "email", GA4GH_PASSPORT_V1_CLAIM, "profile"));
    assertTrue(ProviderUtils.isPassportProvider(providerProperties));
  }

  @Test
  void testNonPassportProvider() {
    var providerProperties =
        ProviderProperties.create().setScopes(Set.of("openid", "email", "profile"));
    assertFalse(ProviderUtils.isPassportProvider(providerProperties));
  }

  @Test
  void testNoScopeProvider() {
    var providerProperties = ProviderProperties.create().setScopes(Set.of());
    assertFalse(ProviderUtils.isPassportProvider(providerProperties));
  }
}
