package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.generated.model.OneOfValidatePassportRequestCriteriaItems;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PassportService {

  private final GA4GHPassportDAO passportDAO;
  private final ExternalCredsConfig externalCredsConfig;
  private final GA4GHVisaDAO visaDAO;
  private final JwtUtils jwtUtils;

  public PassportService(
      GA4GHPassportDAO passportDAO, ExternalCredsConfig externalCredsConfig, GA4GHVisaDAO visaDAO,
      JwtUtils jwtUtils) {
    this.passportDAO = passportDAO;
    this.externalCredsConfig = externalCredsConfig;
    this.visaDAO = visaDAO;
    this.jwtUtils = jwtUtils;
  }

  @ReadTransaction
  public Optional<GA4GHPassport> getPassport(String userId, String providerName) {
    return passportDAO.getPassport(userId, providerName);
  }

  @ReadTransaction
  public List<VisaVerificationDetails> getUnvalidatedAccessTokenVisaDetails() {
    var validationCutoff =
        new Timestamp(
            Instant.now().minus(externalCredsConfig.getTokenValidationDuration()).toEpochMilli());
    return visaDAO.getUnvalidatedAccessTokenVisaDetails(validationCutoff);
  }

  @WriteTransaction
  public void updateVisaLastValidated(int visaId) {
    visaDAO.updateLastValidated(visaId, new Timestamp(Instant.now().toEpochMilli()));
  }

  public void validatePassport(
      String passportJwtString, List<OneOfValidatePassportRequestCriteriaItems> criteria) {
    // parse and validate passport jwt, extract passport and visa objects - throw exception if not
    // valid
    var passportWithVisas = jwtUtils.parsePassportJwtString(passportJwtString);
    
    // lookup passport by jwt id in database - throw exception if not present
    // for each criteria find appropriate VisaComparator and check each appropriate visa
    // return true if a visa is found that matches one of the criteria
  }
}
