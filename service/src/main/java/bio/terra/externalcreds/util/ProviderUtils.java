package bio.terra.externalcreds.util;

import static bio.terra.externalcreds.services.JwtUtils.GA4GH_PASSPORT_V1_CLAIM;

import bio.terra.externalcreds.config.ProviderProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
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

    Map<String, Object> identitiesMap = null;
    String eraUserId = null;
    if (federatedIdentities != null) {
      try {
        if (federatedIdentities instanceof String) {
          identitiesMap = objectMapper.readValue((String) federatedIdentities, Map.class);
        } else if (federatedIdentities instanceof Map<?, ?>) {
          identitiesMap = (Map<String, Object>) federatedIdentities;
        }

        if (identitiesMap != null
            && identitiesMap.containsKey("identities")
            && identitiesMap.get("identities") instanceof List) {
          @SuppressWarnings("unchecked")
          List<Map<String, Object>> identitiesList =
              (List<Map<String, Object>>) identitiesMap.get("identities");

          for (Map<String, Object> identity : identitiesList) {
            if (identity.containsKey("era") && identity.get("era") instanceof Map) {
              @SuppressWarnings("unchecked")
              Map<String, Object> eraInfo = (Map<String, Object>) identity.get("era");
              eraUserId = (String) eraInfo.get("userid");
              if (eraUserId != null) {
                break;
              }
            }
          }
        }
      } catch (IOException e) {
        logger.info("Error parseing federated identities: {}", e.getMessage());
      } catch (Exception e) {
        logger.info("Error extracting linked ERA identity: {}", e.getMessage());
      }
    }
    return eraUserId;
  }
}
