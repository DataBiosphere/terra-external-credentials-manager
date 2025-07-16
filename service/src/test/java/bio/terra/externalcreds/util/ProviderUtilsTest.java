package bio.terra.externalcreds.util;

import static bio.terra.externalcreds.services.JwtUtils.GA4GH_PASSPORT_V1_CLAIM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ProviderProperties;
import java.util.Map;
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

  @Test
  void testGetLinkedEraIdentity() {
    Map<String, Object> identities =
        Map.of("login.gov", Map.of("userid", "12345"), "era", Map.of("userid", "test-era-id"));
    Map<String, Object> federatedIdentities = Map.of("identities", identities);
    assertEquals("test-era-id", ProviderUtils.getLinkedEraIdentity(federatedIdentities));
  }

  @Test
  void testGetLinkedEraIdentityReturnsNull() {
    assertNull(ProviderUtils.getLinkedEraIdentity(null));
    assertNull(ProviderUtils.getLinkedEraIdentity(Map.of()));
    assertNull(ProviderUtils.getLinkedEraIdentity(Map.of("identities", Map.of())));
  }
}
