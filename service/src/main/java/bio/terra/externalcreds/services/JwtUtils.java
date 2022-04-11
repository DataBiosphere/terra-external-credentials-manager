package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.GA4GHVisa.Builder;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.PassportWithVisas;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.google.common.annotations.VisibleForTesting;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jwt.JWTParser;
import java.net.MalformedURLException;
import java.net.URI;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public record JwtUtils(ExternalCredsConfig externalCredsConfig) {

  public static final String PASSPORT_JWT_V11_CLAIM = "passport_jwt_v11";
  public static final String GA4GH_PASSPORT_V1_CLAIM = "ga4gh_passport_v1";
  public static final String GA4GH_VISA_V1_CLAIM = "ga4gh_visa_v1";
  public static final String VISA_TYPE_CLAIM = "type";
  public static final String JKU_HEADER = "jku";
  public static final String JWT_ID_CLAIM = "jti";

  public LinkedAccountWithPassportAndVisas enrichAccountWithPassportAndVisas(
      LinkedAccount linkedAccount, OAuth2User userInfo) {
    String passportJwtString = userInfo.getAttribute(PASSPORT_JWT_V11_CLAIM);
    if (passportJwtString != null) {
      var passportWithVisas = decodeAndValidatePassportJwtString(passportJwtString);

      return new LinkedAccountWithPassportAndVisas.Builder()
          .linkedAccount(linkedAccount)
          .passport(passportWithVisas.getPassport())
          .visas(passportWithVisas.getVisas())
          .build();
    } else {
      return new LinkedAccountWithPassportAndVisas.Builder().linkedAccount(linkedAccount).build();
    }
  }

  public PassportWithVisas decodeAndValidatePassportJwtString(String passportJwtString) {
    var passportJwt = decodeAndValidateJwt(passportJwtString);

    List<String> visaJwtStrings =
        Objects.requireNonNullElse(
            passportJwt.getClaimAsStringList(GA4GH_PASSPORT_V1_CLAIM), Collections.emptyList());

    var visas =
        visaJwtStrings.stream().map(this::decodeAndValidateJwt).map(JwtUtils::buildVisa).toList();

    return new PassportWithVisas.Builder()
        .passport(buildPassport(passportJwt))
        .visas(visas)
        .build();
  }

  private static GA4GHPassport buildPassport(Jwt passportJwt) {
    var passportExpiresAt = getJwtExpires(passportJwt);

    return new GA4GHPassport.Builder()
        .jwt(passportJwt.getTokenValue())
        .expires(passportExpiresAt)
        .jwtId(getJwtClaim(passportJwt, JWT_ID_CLAIM))
        .build();
  }

  private static GA4GHVisa buildVisa(Jwt visaJwt) {
    JSONObject visaClaims = getJwtClaim(visaJwt, GA4GH_VISA_V1_CLAIM);
    var visaType = visaClaims.get(VISA_TYPE_CLAIM);
    if (visaType == null) {
      throw new InvalidJwtException(String.format("visa missing claim [%s]", VISA_TYPE_CLAIM));
    }
    return new Builder()
        .visaType(visaType.toString())
        .jwt(visaJwt.getTokenValue())
        .expires(getJwtExpires(visaJwt))
        .issuer(visaJwt.getIssuer().toString())
        .lastValidated(new Timestamp(Instant.now().toEpochMilli()))
        .tokenType(determineTokenType(visaJwt))
        .build();
  }

  private static Timestamp getJwtExpires(Jwt decodedPassportJwt) {
    var expiresAt = decodedPassportJwt.getExpiresAt();
    if (expiresAt == null) {
      throw new InvalidJwtException("jwt missing expires (exp) claim");
    }
    return new Timestamp(expiresAt.toEpochMilli());
  }

  private static <T> T getJwtClaim(Jwt jwt, String claimName) {
    T claim = jwt.getClaim(claimName);
    return Optional.ofNullable(claim)
        .orElseThrow(
            () -> new InvalidJwtException(String.format("jwt missing claim [%s]", claimName)));
  }

  private static TokenTypeEnum determineTokenType(Jwt visaJwt) {
    // https://github.com/ga4gh/data-security/blob/master/AAI/AAIConnectProfile.md#conformance-for-embedded-token-issuers
    return visaJwt.getHeaders().containsKey(JKU_HEADER)
        ? TokenTypeEnum.document_token
        : TokenTypeEnum.access_token;
  }

  @VisibleForTesting
  Jwt decodeAndValidateJwt(String jwtString) {
    try {
      // first we need to get the issuer from the jwt, the issuer is needed to validate
      var jwt = JWTParser.parse(jwtString);
      var issuer = jwt.getJWTClaimsSet().getIssuer();
      if (issuer == null) {
        throw new InvalidJwtException("jwt missing issuer (iss) claim");
      }

      if (!externalCredsConfig.getAllowedJwtIssuers().contains(URI.create(issuer))) {
        throw new InvalidJwtException(
            String.format("URI [%s] specified by iss claim not on allowed list", issuer));
      }

      // validate the algorithm field
      var allowedAlgorithms = externalCredsConfig.getAllowedJwtAlgorithms();
      var algorithm = ((JWSHeader) jwt.getHeader()).getAlgorithm();

      if (algorithm == null) {
        throw new InvalidJwtException("jwt missing algorithm (alg) header");
      }

      if (!allowedAlgorithms.contains(algorithm.toString())) {
        throw new InvalidJwtException(
            String.format("Algorithm [%s] is not on allowed list", algorithm));
      }

      var jkuOption = Optional.ofNullable(((JWSHeader) jwt.getHeader()).getJWKURL());
      if (jkuOption.isPresent()) {
        // presence of the jku header means the url it specifies contains the key set that must be
        // used validate the signature
        URI jku = jkuOption.get();
        if (externalCredsConfig.getAllowedJwksUris().contains(jku)) {
          return ExternalCredsJwtDecoders.fromJku(jku).decode(jwtString);
        } else {
          throw new InvalidJwtException(
              String.format("URI [%s] specified by jku header not on allowed list", jku));
        }
      } else {
        // no jku means use the issuer to lookup configuration and location of key set
        return JwtDecoders.fromIssuerLocation(issuer).decode(jwtString);
      }
    } catch (ParseException
        | JwtException
        | MalformedURLException
        | IllegalArgumentException
        | IllegalStateException e) {
      throw new InvalidJwtException(e);
    }
  }
}
