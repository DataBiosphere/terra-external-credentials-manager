package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
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

  @ReadTransaction
  public Optional<GA4GHPassport> getGA4GHPassport(String userId, String providerId) {
    var linkedAccount = linkedAccountDAO.getLinkedAccount(userId, providerId);
    if (linkedAccount.isEmpty()) throw new NotFoundException("Linked account not found");
    var linkedAccountId = linkedAccount.get().getId().orElseThrow();
    return ga4ghPassportDAO.getPassport(linkedAccountId);
  }

  @WriteTransaction
  public LinkedAccountWithPassportAndVisas saveLinkedAccount(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    var savedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(linkedAccountWithPassportAndVisas.getLinkedAccount());

    // clear out any passport and visas that may exist and save the new one
    ga4ghPassportDAO.deletePassport(savedLinkedAccount.getId().orElseThrow());

    return savePassportIfExists(
        linkedAccountWithPassportAndVisas.withLinkedAccount(savedLinkedAccount));
  }

  @WriteTransaction
  public boolean deleteLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.deleteLinkedAccountIfExists(userId, providerId);
  }

  private LinkedAccountWithPassportAndVisas savePassportIfExists(
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
