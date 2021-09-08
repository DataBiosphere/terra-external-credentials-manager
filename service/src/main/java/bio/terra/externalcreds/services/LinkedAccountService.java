package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LinkedAccountService {

  private final LinkedAccountDAO linkedAccountDAO;
  private final GA4GHPassportDAO ga4ghPassportDAO;
  private final GA4GHVisaDAO ga4ghVisaDAO;

  public LinkedAccountService(
      LinkedAccountDAO linkedAccountDAO,
      GA4GHPassportDAO ga4ghPassportDAO,
      GA4GHVisaDAO ga4ghVisaDAO) {
    this.linkedAccountDAO = linkedAccountDAO;
    this.ga4ghPassportDAO = ga4ghPassportDAO;
    this.ga4ghVisaDAO = ga4ghVisaDAO;
  }

  @ReadTransaction
  public Optional<LinkedAccount> getLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.getLinkedAccount(userId, providerId);
  }

  @WriteTransaction
  public LinkedAccountWithPassportAndVisas upsertLinkedAccountWithPassportAndVisas(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    var savedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(linkedAccountWithPassportAndVisas.getLinkedAccount());

    // clear out any passport and visas that may exist and save the new one
    ga4ghPassportDAO.deletePassport(savedLinkedAccount.getId().orElseThrow());

    return savePassportAndVisasIfPresent(
        linkedAccountWithPassportAndVisas.withLinkedAccount(savedLinkedAccount));
  }

  @WriteTransaction
  public LinkedAccount upsertLinkedAccount(LinkedAccount linkedAccount) {
    return linkedAccountDAO.upsertLinkedAccount(linkedAccount);
  }

  @WriteTransaction
  public boolean deleteLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.deleteLinkedAccountIfExists(userId, providerId);
  }

  @ReadTransaction
  public List<LinkedAccount> getExpiringLinkedAccounts(Timestamp expirationCutoff) {
    return linkedAccountDAO.getExpiringLinkedAccounts(expirationCutoff);
  }

  private LinkedAccountWithPassportAndVisas savePassportAndVisasIfPresent(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    if (linkedAccountWithPassportAndVisas.getPassport().isPresent()) {

      var savedPassport =
          ga4ghPassportDAO.insertPassport(
              linkedAccountWithPassportAndVisas
                  .getPassport()
                  .get()
                  .withLinkedAccountId(
                      linkedAccountWithPassportAndVisas.getLinkedAccount().getId()));

      var savedVisas =
          linkedAccountWithPassportAndVisas.getVisas().stream()
              .map(v -> ga4ghVisaDAO.insertVisa(v.withPassportId(savedPassport.getId())))
              .collect(Collectors.toList());

      return linkedAccountWithPassportAndVisas.withPassport(savedPassport).withVisas(savedVisas);
    } else {
      return linkedAccountWithPassportAndVisas;
    }
  }
}
