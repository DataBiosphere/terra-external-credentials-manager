package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.dataAccess.ReadTransaction;
import bio.terra.externalcreds.models.LinkedAccount;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LinkedAccountService {

  private final LinkedAccountDAO linkedAccountDAO;
  private final ProviderService providerService;

  public LinkedAccountService(LinkedAccountDAO linkedAccountDAO, ProviderService providerService) {
    this.linkedAccountDAO = linkedAccountDAO;
    this.providerService = providerService;
  }

  @ReadTransaction
  public LinkedAccount getLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.getLinkedAccount(userId, providerId);
  }

  public boolean deleteLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.deleteLinkedAccount(userId, providerId);
  }

  public void revokeProviderLink(String userId, String providerId) {
    LinkedAccount linkedAccount = getLinkedAccount(userId, providerId);
    ProviderConfig.ProviderInfo providerInfo = providerService.getProviderInfo(providerId);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("client_id", providerInfo.getClientId());
    queryParams.put("client_secret", providerInfo.getClientSecret());
    queryParams.put("token", linkedAccount.getRefreshToken());
    queryParams.put("token_type_hint", "refresh_token");
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.put(providerInfo.getRevokeEndpoint(), queryParams);
  }
}
