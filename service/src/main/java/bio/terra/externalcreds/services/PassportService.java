package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.generated.model.OneOfValidatePassportRequestCriteriaItems;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import bio.terra.externalcreds.visaComparators.VisaComparator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PassportService {

  private final LinkedAccountDAO linkedAccountDAO;
  private final GA4GHPassportDAO passportDAO;
  private final ExternalCredsConfig externalCredsConfig;
  private final GA4GHVisaDAO visaDAO;
  private final JwtUtils jwtUtils;
  private final Collection<VisaComparator> visaComparators;

  public PassportService(
      LinkedAccountDAO linkedAccountDAO,
      GA4GHPassportDAO passportDAO,
      ExternalCredsConfig externalCredsConfig,
      GA4GHVisaDAO visaDAO,
      Collection<VisaComparator> visaComparators,
      JwtUtils jwtUtils) {
    this.linkedAccountDAO = linkedAccountDAO;
    this.passportDAO = passportDAO;
    this.externalCredsConfig = externalCredsConfig;
    this.visaDAO = visaDAO;
    this.jwtUtils = jwtUtils;
    this.visaComparators = visaComparators;
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

  public boolean validatePassport(
      String passportJwtString, List<OneOfValidatePassportRequestCriteriaItems> criteria) {
    // parse and validate passport jwt, extract passport and visa objects - throw exception if not
    // valid
    var passportWithVisas = jwtUtils.parsePassportJwtString(passportJwtString);

    // lookup passport by jwt id in database - throw exception if not present
    var jwtId = passportWithVisas.getPassport().get().getJwtId();
    var linkedAccount = linkedAccountDAO.getLinkedAccountByPassportJwtId(jwtId);

    // for each criteria find appropriate VisaComparator and check each appropriate visa
    // return true if a visa is found that matches one of the criteria
    var comparator = getVisaComparator(criteria.get(0));
    return passportWithVisas.getVisas().stream().anyMatch(v -> comparator.get().matchesCriterion(v, criteria.get(0)));
  }

  private Optional<VisaComparator> getVisaComparator(
      OneOfValidatePassportRequestCriteriaItems criterion) {
    return visaComparators.stream().filter(c -> c.criterionTypeSupported(criterion)).findFirst();
  }
}
