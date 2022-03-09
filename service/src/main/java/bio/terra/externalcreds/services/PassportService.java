package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.PassportWithVisas;
import bio.terra.externalcreds.models.ValidatePassportResult;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import bio.terra.externalcreds.visaComparators.VisaComparator;
import bio.terra.externalcreds.visaComparators.VisaCriterion;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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

  /**
   * @param passportJwtStrings
   * @param criteria
   * @return
   */
  @ReadTransaction
  public ValidatePassportResult validatePassport(
      Collection<String> passportJwtStrings, Collection<VisaCriterion> criteria) {

    var passports = decodeAndValidatePassports(passportJwtStrings);
    var linkedAccount = getSingleLinkedAccountForAllPassports(passports);

    for (var passportWithVisas : passports) {
      for (var criterion : criteria) {
        for (var visa : passportWithVisas.getVisas()) {
          if (getVisaComparator(criterion).matchesCriterion(visa, criterion)) {
            return new ValidatePassportResult.Builder()
                .valid(true)
                .matchedCriterion(criterion)
                .auditInfo(
                    Map.of(
                        "passport_jti",
                        passportWithVisas.getPassport().getJwtId(),
                        "external_user_id",
                        linkedAccount.getExternalUserId(),
                        "internal_user_id",
                        linkedAccount.getUserId()))
                .build();
          }
        }
      }
    }

    // if we got this far there was no matching visa
    return new ValidatePassportResult.Builder()
        .valid(false)
        .auditInfo(
            Map.of(
                "external_user_id",
                linkedAccount.getExternalUserId(),
                "internal_user_id",
                linkedAccount.getUserId()))
        .build();
  }

  private Collection<PassportWithVisas> decodeAndValidatePassports(
      Collection<String> passportJwtStrings) {
    try {
      return passportJwtStrings.stream()
          .map(jwtUtils::decodeAndValidatePassportJwtString)
          .collect(Collectors.toList());
    } catch (InvalidJwtException e) {
      throw new BadRequestException("invalid passport jwt", e);
    }
  }

  private LinkedAccount getSingleLinkedAccountForAllPassports(
      Collection<PassportWithVisas> passportWithVisas) {
    var linkedAccounts =
        passportWithVisas.stream()
            .flatMap(
                p ->
                    linkedAccountDAO
                        .getLinkedAccountByPassportJwtId(p.getPassport().getJwtId())
                        .stream())
            .collect(Collectors.toSet());

    if (linkedAccounts.isEmpty()) {
      throw new BadRequestException("unknown passport");
    }
    if (linkedAccounts.size() > 1) {
      throw new BadRequestException(
          "a single request validate passport can contain only passports from the same linked account");
    }
    return linkedAccounts.iterator().next();
  }

  private VisaComparator getVisaComparator(VisaCriterion criterion) {
    return visaComparators.stream()
        .filter(c -> c.criterionTypeSupported(criterion))
        .findFirst()
        .orElseThrow(
            () ->
                new ExternalCredsException(
                    String.format("comparator not found for visa criterion %s", criterion)));
  }
}
