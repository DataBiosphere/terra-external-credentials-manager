package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.*;
import bio.terra.externalcreds.models.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class LinkedAccountService {

  private final LinkedAccountDAO linkedAccountDAO;
  private final GA4GHPassportDAO ga4ghPassportDAO;
  private final GA4GHVisaDAO ga4ghVisaDAO;
  private final PassportService passportService;

  public LinkedAccountService(
      LinkedAccountDAO linkedAccountDAO,
      GA4GHPassportDAO ga4ghPassportDAO,
      GA4GHVisaDAO ga4ghVisaDAO,
      PassportService passportService) {
    this.linkedAccountDAO = linkedAccountDAO;
    this.ga4ghPassportDAO = ga4ghPassportDAO;
    this.ga4ghVisaDAO = ga4ghVisaDAO;
    this.passportService = passportService;
  }

  @ReadTransaction
  public LinkedAccount getLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.getLinkedAccount(userId, providerId);
  }

  @WriteTransaction
  public LinkedAccount saveLinkedAccount(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    var savedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(linkedAccountWithPassportAndVisas.getLinkedAccount());

    // clear out any passport and visas that may exist and save the new one
    passportService.deletePassportAndContainedVisas(savedLinkedAccount.getId());
    savePassportIfExists(linkedAccountWithPassportAndVisas, savedLinkedAccount);

    return savedLinkedAccount;
  }

  private void savePassportIfExists(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas,
      LinkedAccount savedLinkedAccount) {
    if (linkedAccountWithPassportAndVisas.getPassport() != null) {

      var savedPassport =
          ga4ghPassportDAO.insertPassport(
              linkedAccountWithPassportAndVisas
                  .getPassport()
                  .withLinkedAccountId(savedLinkedAccount.getId()));

      List<GA4GHVisa> visas =
          Objects.requireNonNullElse(
              linkedAccountWithPassportAndVisas.getVisas(), Collections.emptyList());

      for (var visa : visas) {
        ga4ghVisaDAO.insertVisa(visa.withPassportId(savedPassport.getId()));
      }
    }
  }
}
