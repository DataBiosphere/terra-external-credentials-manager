package bio.terra.externalcreds.services;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.models.*;
import com.nimbusds.jwt.JWTParser;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
    ProviderConfig.ProviderInfo providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      return null;
    }

    ClientRegistration providerClient = providerClientCache.getProviderClient(provider);

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

    ProviderConfig.ProviderInfo providerInfo = providerConfig.getServices().get(provider);
    if (providerInfo == null) {
      throw new NotFoundException(String.format("provider %s not found", provider));
    }

    ClientRegistration providerClient = providerClientCache.getProviderClient(provider);

    OAuth2AccessTokenResponse tokenResponse =
        oAuth2Service.authorizationCodeExchange(
            providerClient,
            authorizationCode,
            redirectUri,
            scopes,
            state,
            providerInfo.getAdditionalAuthorizationParameters());

    OAuth2RefreshToken refreshToken = tokenResponse.getRefreshToken();
    if (refreshToken == null) {
      throw new ExternalCredsException(
          "cannot link account because authorization response did not contain refresh token");
    }

    Timestamp expires =
        new Timestamp(Instant.now().plus(providerInfo.getLinkLifespan()).toEpochMilli());

    OAuth2User userInfo = oAuth2Service.getUserInfo(providerClient, tokenResponse.getAccessToken());

    LinkedAccount linkedAccount =
        LinkedAccount.builder()
            .providerId(provider)
            .userId(userId)
            .expires(expires)
            .externalUserId(userInfo.getAttribute(EXTERNAL_USERID_ATTR))
            .refreshToken(refreshToken.getTokenValue())
            .build();

    String passportJwtString = userInfo.getAttribute(PASSPORT_JWT_V11_CLAIM);
    if (passportJwtString != null) {
      Jwt passportJwt = decodeJwt(passportJwtString);
      GA4GHPassport passport = buildPassport(passportJwt);

      List<String> visaJwtStrings =
          Objects.requireNonNullElse(
              passportJwt.getClaimAsStringList(GA4GH_PASSPORT_V1_CLAIM), Collections.emptyList());

      List<GA4GHVisa> visas = new ArrayList<>(visaJwtStrings.size());
      for (var visaJwtString : visaJwtStrings) {
        visas.add(buildVisa(decodeJwt(visaJwtString)));
      }

      return LinkedAccountWithPassportAndVisas.builder()
          .linkedAccount(linkedAccount)
          .passport(passport)
          .visas(visas)
          .build();
    } else {
      return LinkedAccountWithPassportAndVisas.builder().linkedAccount(linkedAccount).build();
    }
  }

  private Jwt decodeJwt(String jwt) {
    try {
      // first we need to get the issuer from the jwt, the issuer is needed to validate
      String issuer = JWTParser.parse(jwt).getJWTClaimsSet().getIssuer();
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
        ? TokenTypeEnum.DOCUMENT_TOKEN
        : TokenTypeEnum.ACCESS_TOKEN;
  }

  private Timestamp getJwtExpires(Jwt decodedPassportJwt) {
    Instant expiresAt = decodedPassportJwt.getExpiresAt();
    if (expiresAt == null) {
      throw new InvalidJwtException("jwt missing expires (exp) claim");
    }
    return new Timestamp(expiresAt.toEpochMilli());
  }

  private String getJwtClaim(Jwt jwt, String claimName) {
    String claim = jwt.getClaimAsString(claimName);
    if (claim == null) {
      throw new InvalidJwtException("jwt missing claim " + claimName);
    }
    return claim;
  }

  private GA4GHPassport buildPassport(Jwt passportJwt) {
    Timestamp passportExpiresAt = getJwtExpires(passportJwt);

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
