package bio.terra.externalcreds.util;

import bio.terra.externalcreds.generated.model.Provider;
import java.util.Set;

public class ProviderUtils {
  private static Set<Provider> fenceProviders =
      Set.of(Provider.FENCE, Provider.DCF_FENCE, Provider.KIDS_FIRST, Provider.ANVIL);

  public static boolean isFenceProvider(Provider provider) {
    return fenceProviders.contains(provider);
  }
}
