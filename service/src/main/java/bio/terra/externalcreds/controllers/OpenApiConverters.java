package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.OneOfValidatePassportRequestCriteriaItems;
import bio.terra.externalcreds.generated.model.OneOfValidatePassportResultMatchedCriterion;
import bio.terra.externalcreds.generated.model.RASv1Dot1VisaCriterion;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.ValidatePassportResult;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import bio.terra.externalcreds.models.ValidatePassportResultInternal;
import bio.terra.externalcreds.visaComparators.RASv1Dot1VisaCriterionInternal;
import bio.terra.externalcreds.visaComparators.VisaCriterionInternal;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Utilities for converting classes generated from openapi.yml to and from internal ECM model
 * classes
 */
public class OpenApiConverters {

  /** Converts openapi inputs to internal ECM models */
  public static class Input {
    public static Collection<VisaCriterionInternal> convert(
        Collection<OneOfValidatePassportRequestCriteriaItems> criteria) {
      return criteria.stream()
          .map(
              c -> {
                if (c instanceof RASv1Dot1VisaCriterion rasCrit) {
                  return new RASv1Dot1VisaCriterionInternal.Builder()
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

  /** Converts internal ECM models to openapi outputs */
  public static class Output {
    public static ValidatePassportResult convert(ValidatePassportResultInternal result) {
      var returnVal = new ValidatePassportResult();
      returnVal.setValid(result.getValid());
      result.getAuditInfo().ifPresent(returnVal::setAuditInfo);
      result.getMatchedCriterion().ifPresent(c -> returnVal.setMatchedCriterion(convert(c)));
      return returnVal;
    }

    public static OneOfValidatePassportResultMatchedCriterion convert(
        VisaCriterionInternal visaCriterion) {
      if (visaCriterion instanceof RASv1Dot1VisaCriterionInternal rasCrit) {
        var converted =
            new RASv1Dot1VisaCriterion()
                .consentCode(rasCrit.getConsentCode())
                .phsId(rasCrit.getPhsId());
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

    public static SshKeyPair convert(SshKeyPairInternal sshKeyPairInternal) {
      return new SshKeyPair()
          .externalUserEmail(sshKeyPairInternal.getExternalUserEmail())
          .publicKey(sshKeyPairInternal.getPublicKey())
          .privateKey(new String(sshKeyPairInternal.getPrivateKey(), StandardCharsets.UTF_8));
    }
  }
}
