package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.generated.model.AdminLinkInfo;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.RASv1Dot1VisaCriterion;
import bio.terra.externalcreds.generated.model.RasDbGapPermission;
import bio.terra.externalcreds.generated.model.RasLinkInfo;
import bio.terra.externalcreds.generated.model.RasPassportInfo;
import bio.terra.externalcreds.generated.model.RasSupportInfo;
import bio.terra.externalcreds.generated.model.ValidatePassportResult;
import bio.terra.externalcreds.generated.model.VisaCriterion;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.ValidatePassportResultInternal;
import bio.terra.externalcreds.visaComparators.RASv1Dot1VisaComparator;
import bio.terra.externalcreds.visaComparators.RASv1Dot1VisaCriterionInternal;
import bio.terra.externalcreds.visaComparators.VisaCriterionInternal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utilities for converting classes generated from openapi.yml to and from internal ECM model
 * classes
 */
public class OpenApiConverters {

  /** Converts openapi inputs to internal ECM models */
  public static class Input {
    public static Collection<VisaCriterionInternal> convert(Collection<VisaCriterion> criteria) {
      return criteria.stream()
          .map(
              c -> {
                if (c instanceof RASv1Dot1VisaCriterion rasCrit) {
                  return new RASv1Dot1VisaCriterionInternal.Builder()
                      .type(rasCrit.getType())
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

    public static VisaCriterion convert(VisaCriterionInternal visaCriterion) {
      if (visaCriterion instanceof RASv1Dot1VisaCriterionInternal rasCrit) {
        var converted =
            new RASv1Dot1VisaCriterion()
                .consentCode(rasCrit.getConsentCode())
                .phsId(rasCrit.getPhsId());
        converted.issuer(rasCrit.getIssuer());
        converted.type(rasCrit.getType());
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

    public static AdminLinkInfo convertAdmin(LinkedAccount linkedAccount) {
      return new AdminLinkInfo()
          .linkedExternalId(linkedAccount.getExternalUserId())
          .linkExpireTime(linkedAccount.getExpires())
          .userId(linkedAccount.getUserId());
    }

    public static RasSupportInfo convertRasSupportInfo(
        LinkedAccount link,
        Optional<GA4GHPassport> passport,
        List<RASv1Dot1VisaComparator.DbGapPermission> permissions) {
      var linkInfo =
          new RasLinkInfo()
              .externalUserId(link.getExternalUserId())
              .linkExpires(link.getExpires())
              .authenticated(link.isAuthenticated())
              .expired(link.isExpired());
      var supportInfo = new RasSupportInfo().link(linkInfo);
      passport.ifPresent(
          p -> {
            supportInfo.passport(
                new RasPassportInfo()
                    .passportExpires(p.getExpires())
                    .expired(p.getExpires().toInstant().isBefore(Instant.now())));
            supportInfo.dbgapPermissions(
                permissions.stream()
                    .map(
                        perm ->
                            new RasDbGapPermission()
                                .phsId(perm.getPhsId())
                                .consentGroup(perm.getConsentGroup())
                                .role(perm.getRole()))
                    .toList());
          });
      return supportInfo;
    }
  }
}
