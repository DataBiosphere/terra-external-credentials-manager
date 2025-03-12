package bio.terra.externalcreds.util;

import static bio.terra.externalcreds.services.JwtUtils.GA4GH_PASSPORT_V1_CLAIM;

import bio.terra.externalcreds.config.ProviderProperties;

public class ProviderUtils {

  private ProviderUtils() {}

  public static boolean isPassportProvider(ProviderProperties providerProperties) {
    return providerProperties.getScopes().contains(GA4GH_PASSPORT_V1_CLAIM);
  }
}
