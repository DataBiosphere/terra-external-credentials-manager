package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.PassportVerificationDetails;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PassportService {

  private final GA4GHPassportDAO passportDAO;

  public PassportService(GA4GHPassportDAO passportDAO) {
    this.passportDAO = passportDAO;
  }

  @ReadTransaction
  public Optional<GA4GHPassport> getPassport(String userId, String providerId) {
    return passportDAO.getPassport(userId, providerId);
  }

  @WriteTransaction
  public boolean deletePassport(int linkedAccountId) {
    return passportDAO.deletePassport(linkedAccountId);
  }

  @ReadTransaction
  public List<PassportVerificationDetails> getPassportsWithUnvalidatedAccessTokenVisas() {
    return passportDAO.getPassportsWithUnvalidatedAccessTokenVisas();
  }
}
