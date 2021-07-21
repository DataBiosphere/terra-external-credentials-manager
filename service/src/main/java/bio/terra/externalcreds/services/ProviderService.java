package bio.terra.externalcreds.services;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.nimbusds.jwt.JWTParser;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ProviderService {

  public static final String PASSPORT_JWT_V11_CLAIM = "passport_jwt_v11";
  public static final String GA4GH_PASSPORT_V1_CLAIM = "ga4gh_passport_v1";
  public static final String VISA_TYPE_CLAIM = "type";
  public static final String JKU_HEADER = "jku";
  public static final String EXTERNAL_USERID_ATTR = "email";

  private final ProviderConfig providerConfig;
  private final ProviderClientCache providerClientCache;
  private final OAuth2Service oAuth2Service;

  public ProviderService(
      ProviderConfig providerConfig,
      ProviderClientCache providerClientCache,
      OAuth2Service oAuth2Service) {
    this.providerConfig = providerConfig;
    this.providerClientCache = providerClientCache;
    this.oAuth2Service = oAuth2Service;
  }

  public Set<String> getProviderList() {
    return Collections.unmodifiableSet(providerConfig.getServices().keySet());
  }

  public String getProviderAuthorizationUrl(
      String provider, String redirectUri, Set<String> scopes, String state) {
    var providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      return null;
    }

    var providerClient = providerClientCache.getProviderClient(provider);

    return oAuth2Service.getAuthorizationRequestUri(
        providerClient,
        redirectUri,
        scopes,
        state,
        providerInfo.getAdditionalAuthorizationParameters());
  }

  public LinkedAccountWithPassportAndVisas useAuthorizationCodeToGetLinkedAccount(
      String provider,
      String userId,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state) {

    var providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      throw new NotFoundException(String.format("provider %s not found", provider));
    }

    var providerClient = providerClientCache.getProviderClient(provider);

    var tokenResponse =
        oAuth2Service.authorizationCodeExchange(
            providerClient,
            authorizationCode,
            redirectUri,
            scopes,
            state,
            providerInfo.getAdditionalAuthorizationParameters());

    var refreshToken = tokenResponse.getRefreshToken();
    if (refreshToken == null) {
      throw new ExternalCredsException(
          "cannot link account because authorization response did not contain refresh token");
    }

    var expires = new Timestamp(Instant.now().plus(providerInfo.getLinkLifespan()).toEpochMilli());

    var userInfo = oAuth2Service.getUserInfo(providerClient, tokenResponse.getAccessToken());

    var linkedAccount =
        LinkedAccount.builder()
            .providerId(provider)
            .userId(userId)
            .expires(expires)
            .externalUserId(userInfo.getAttribute(EXTERNAL_USERID_ATTR))
            .refreshToken(refreshToken.getTokenValue())
            .build();

    var passportJwtString = userInfo.<String>getAttribute(PASSPORT_JWT_V11_CLAIM);
    if (passportJwtString != null) {
      var passportJwt = decodeJwt(passportJwtString);

      var visaJwtStrings =
          Objects.requireNonNullElse(
              passportJwt.getClaimAsStringList(GA4GH_PASSPORT_V1_CLAIM),
              Collections.<String>emptyList());

      var visas =
          visaJwtStrings.stream().map(s -> buildVisa(decodeJwt(s))).collect(Collectors.toList());

      return LinkedAccountWithPassportAndVisas.builder()
          .linkedAccount(linkedAccount)
          .passport(buildPassport(passportJwt))
          .visas(visas)
          .build();
    } else {
      return LinkedAccountWithPassportAndVisas.builder().linkedAccount(linkedAccount).build();
    }
  }

  private Jwt decodeJwt(String jwt) {
    try {
      // first we need to get the issuer from the jwt, the issuer is needed to validate
      var issuer = JWTParser.parse(jwt).getJWTClaimsSet().getIssuer();
      if (issuer == null) {
        throw new InvalidJwtException("jwt missing issuer (iss) claim");
      }
      return JwtDecoders.fromIssuerLocation(issuer).decode(jwt);
    } catch (ParseException | JwtException e) {
      throw new InvalidJwtException(e);
    }
  }

  private TokenTypeEnum determineTokenType(Jwt visaJwt) {
    // https://github.com/ga4gh/data-security/blob/master/AAI/AAIConnectProfile.md#conformance-for-embedded-token-issuers
    return visaJwt.getHeaders().containsKey(JKU_HEADER)
        ? TokenTypeEnum.document_token
        : TokenTypeEnum.access_token;
  }

  private Timestamp getJwtExpires(Jwt decodedPassportJwt) {
    var expiresAt = decodedPassportJwt.getExpiresAt();
    if (expiresAt == null) {
      throw new InvalidJwtException("jwt missing expires (exp) claim");
    }
    return new Timestamp(expiresAt.toEpochMilli());
  }

  private String getJwtClaim(Jwt jwt, String claimName) {
    var claim = jwt.getClaimAsString(claimName);
    if (claim == null) {
      throw new InvalidJwtException("jwt missing claim " + claimName);
    }
    return claim;
  }

  private GA4GHPassport buildPassport(Jwt passportJwt) {
    var passportExpiresAt = getJwtExpires(passportJwt);

    return GA4GHPassport.builder()
        .jwt(passportJwt.getTokenValue())
        .expires(passportExpiresAt)
        .build();
  }

  private GA4GHVisa buildVisa(Jwt visaJwt) {
    return GA4GHVisa.builder()
        .visaType(getJwtClaim(visaJwt, VISA_TYPE_CLAIM))
        .jwt(visaJwt.getTokenValue())
        .expires(getJwtExpires(visaJwt))
        .issuer(visaJwt.getIssuer().toString())
        .lastValidated(new Timestamp(Instant.now().toEpochMilli()))
        .tokenType(determineTokenType(visaJwt))
        .build();
  }
}
