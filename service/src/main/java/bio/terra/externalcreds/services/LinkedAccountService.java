package bio.terra.externalcreds.services;

import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccount;
import org.springframework.stereotype.Service;

@Service
public class LinkedAccountService {

  private final LinkedAccountDAO linkedAccountDAO;

  public LinkedAccountService(LinkedAccountDAO linkedAccountDAO) {
    this.linkedAccountDAO = linkedAccountDAO;
  }

  public LinkedAccount getLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.getLinkedAccount(userId, providerId);
  }

  public void deleteLinkedAccount(String userId, String providerId) {
    linkedAccountDAO.deleteLinkedAccount(userId, providerId);
  }
}
