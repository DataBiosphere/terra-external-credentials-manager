package bio.terra.externalcreds.services;

import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import org.springframework.stereotype.Service;

@Service
public class PassportService {

  private final GA4GHPassportDAO ga4ghPassportDAO;
  private final GA4GHVisaDAO ga4ghVisaDAO;

  public PassportService(GA4GHPassportDAO ga4ghPassportDAO, GA4GHVisaDAO ga4GHVisaDAO) {
    this.ga4ghPassportDAO = ga4ghPassportDAO;
    this.ga4ghVisaDAO = ga4GHVisaDAO;
  }

  @WriteTransaction
  public void deletePassportAndContainedVisas(int linkedAccountId) {
    var passport = ga4ghPassportDAO.getPassport(linkedAccountId);
    ga4ghVisaDAO.deleteVisas(passport.getId());
    ga4ghPassportDAO.deletePassport(linkedAccountId);
  }
}
