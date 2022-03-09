package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.OneOfValidatePassportRequestCriteriaItems;
import bio.terra.externalcreds.generated.model.OneOfValidatePassportResultMatchedCriterion;
import bio.terra.externalcreds.generated.model.RASv11;
import bio.terra.externalcreds.generated.model.ValidatePassportResult;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.visaComparators.RASv1Dot1Criterion;
import bio.terra.externalcreds.visaComparators.VisaCriterion;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Utilities for converting classes generated from openapi.yml to and from internal ECM model
 * classes
 */
public class OpenApiConverters {

  /** Converts openapi inputs to ECM models */
  public static class Input {
    public static Collection<VisaCriterion> convert(
        Collection<OneOfValidatePassportRequestCriteriaItems> criteria) {
      return criteria.stream()
          .map(
              c -> {
                if (c instanceof RASv11) {
                  var rasCrit = (RASv11) c;
                  return new RASv1Dot1Criterion.Builder()
                      .issuer(rasCrit.getIssuer())
                      .phsId(rasCrit.getPhsId())
                      .consentCode(rasCrit.getConsentCode())
                      .build();
                } else {
                  throw new BadRequestException(String.format("unknown visa criterion %s", c));
                }
              })
          .collect(Collectors.toList());
    }
  }

  /** Converts ECM models to openapi outputs */
  public static class Output {
    public static ValidatePassportResult convert(
        bio.terra.externalcreds.models.ValidatePassportResult result) {
      var returnVal = new ValidatePassportResult();
      returnVal.setValid(result.getValid());
      result.getAuditInfo().ifPresent(returnVal::setAuditInfo);
      result.getMatchedCriterion().ifPresent(c -> returnVal.setMatchedCriterion(convert(c)));
      return returnVal;
    }

    public static OneOfValidatePassportResultMatchedCriterion convert(VisaCriterion visaCriterion) {
      if (visaCriterion instanceof RASv1Dot1Criterion) {
        var rasCrit = (RASv1Dot1Criterion) visaCriterion;
        var converted =
            new RASv11().consentCode(rasCrit.getConsentCode()).phsId(rasCrit.getPhsId());
        converted.issuer(rasCrit.getIssuer());
        return converted;
      } else {
        throw new ExternalCredsException(String.format("unknown visa criterion %s", visaCriterion));
      }
    }

    public static LinkInfo convert(LinkedAccount linkedAccount) {
      return new LinkInfo()
          .externalUserId(linkedAccount.getExternalUserId())
          .expirationTimestamp(linkedAccount.getExpires())
          .authenticated(linkedAccount.isAuthenticated());
    }
  }
}