package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.GA4GHVisa.Builder;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
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
import java.util.stream.Collectors;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class JwtUtils {
  public static final String PASSPORT_JWT_V11_CLAIM = "passport_jwt_v11";
  public static final String GA4GH_PASSPORT_V1_CLAIM = "ga4gh_passport_v1";
  public static final String GA4GH_VISA_V1_CLAIM = "ga4gh_visa_v1";
  public static final String VISA_TYPE_CLAIM = "type";
  public static final String JKU_HEADER = "jku";

  private final ExternalCredsConfig externalCredsConfig;

  public JwtUtils(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  public LinkedAccountWithPassportAndVisas enrichAccountWithPassportAndVisas(
      LinkedAccount linkedAccount, OAuth2User userInfo) {
    String passportJwtString = userInfo.getAttribute(PASSPORT_JWT_V11_CLAIM);
    if (passportJwtString != null) {
      var passportJwt = decodeJwt(passportJwtString);

      List<String> visaJwtStrings =
          Objects.requireNonNullElse(
              passportJwt.getClaimAsStringList(GA4GH_PASSPORT_V1_CLAIM),
              Collections.emptyList());

      var visas =
          visaJwtStrings.stream().map(this::decodeJwt).map(this::buildVisa).collect(Collectors.toList());

      return new LinkedAccountWithPassportAndVisas.Builder()
          .linkedAccount(linkedAccount)
          .passport(buildPassport(passportJwt))
          .visas(visas)
          .build();
    } else {
      return new LinkedAccountWithPassportAndVisas.Builder().linkedAccount(linkedAccount).build();
    }
  }

  private static GA4GHPassport buildPassport(Jwt passportJwt) {
    var passportExpiresAt = getJwtExpires(passportJwt);

    return new GA4GHPassport.Builder()
        .jwt(passportJwt.getTokenValue())
        .expires(passportExpiresAt)
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
    return Optional.ofNullable(jwt.getClaim(claimName)).orElseThrow(() -> new InvalidJwtException(String.format("jwt missing claim [%s]", claimName)));
    if (claim == null) {
      throw new InvalidJwtException(String.format("jwt missing claim [%s]", claimName));
    }
    return claim;
  }

  private static TokenTypeEnum determineTokenType(Jwt visaJwt) {
    // https://github.com/ga4gh/data-security/blob/master/AAI/AAIConnectProfile.md#conformance-for-embedded-token-issuers
    return visaJwt.getHeaders().containsKey(JKU_HEADER)
        ? TokenTypeEnum.document_token
        : TokenTypeEnum.access_token;
  }

  @VisibleForTesting
  Jwt decodeJwt(String jwtString) {
    try {
      // first we need to get the issuer from the jwt, the issuer is needed to validate
      var jwt = JWTParser.parse(jwtString);
      var issuer = jwt.getJWTClaimsSet().getIssuer();
      if (issuer == null) {
        throw new InvalidJwtException("jwt missing issuer (iss) claim");
      }
      var jkuOption = Optional.ofNullable(((JWSHeader) jwt.getHeader()).getJWKURL());
      return jkuOption.map(jku ->
      if (jkuOption.isPresent()) {
        if (externalCredsConfig.getAllowedJwksUris().contains(jku)) {
          return ExternalCredsJwtDecoders.fromJku(jku).decode(jwtString);
        } else {
          throw new InvalidJwtException(
              String.format("URI [%s] specified by jku header not on allowed list", jku));
        }).orElseGet(() -> JwtDecoders.fromIssuerLocation(issuer).decode(jwtString))
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
