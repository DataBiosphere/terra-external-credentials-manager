package bio.terra.externalcreds.util;

import static bio.terra.externalcreds.services.JwtUtils.GA4GH_PASSPORT_V1_CLAIM;

import bio.terra.externalcreds.config.ProviderProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviderUtils {

  private static final Logger logger = LoggerFactory.getLogger(ProviderUtils.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private ProviderUtils() {}

  public static boolean isPassportProvider(ProviderProperties providerProperties) {
    return providerProperties.getScopes().contains(GA4GH_PASSPORT_V1_CLAIM);
  }

  public static String getLinkedEraIdentity(Object federatedIdentities) {
    logger.info("Federated Identities: {}", federatedIdentities);
    String eraUserId = null;

    if (federatedIdentities != null) {
      try {
        Map<String, Object> identitiesMap = (Map<String, Object>) federatedIdentities;
        if (identitiesMap != null && identitiesMap.containsKey("identities")) {
          @SuppressWarnings("unchecked")
          Map<String, Object> identities = (Map<String, Object>) identitiesMap.get("identities");

          if (identities.containsKey("era")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> eraInfo = (Map<String, Object>) identities.get("era");
            eraUserId = (String) eraInfo.get("userid");
          }
        }
      } catch (Exception e) {
        logger.info("Error extracting linked ERA identity: {}", e.getMessage());
      }
    }
    return eraUserId;
  }
}
