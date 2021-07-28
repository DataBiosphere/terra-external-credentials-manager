package bio.terra.externalcreds.services;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.config.ProviderInfo;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.google.common.annotations.VisibleForTesting;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jwt.JWTParser;
import java.net.MalformedURLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ProviderService {

  public static final String PASSPORT_JWT_V11_CLAIM = "passport_jwt_v11";
  public static final String GA4GH_PASSPORT_V1_CLAIM = "ga4gh_passport_v1";
  public static final String GA4GH_VISA_V1_CLAIM = "ga4gh_visa_v1";
  public static final String VISA_TYPE_CLAIM = "type";
  public static final String JKU_HEADER = "jku";
  public static final String EXTERNAL_USERID_ATTR = "email";

  private final ProviderConfig providerConfig;
  private final ProviderClientCache providerClientCache;
  private final OAuth2Service oAuth2Service;
  private final LinkedAccountService linkedAccountService;

  public ProviderService(
      ProviderConfig providerConfig,
      ProviderClientCache providerClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService) {
    this.providerConfig = providerConfig;
    this.providerClientCache = providerClientCache;
    this.oAuth2Service = oAuth2Service;
    this.linkedAccountService = linkedAccountService;
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

  public LinkedAccountWithPassportAndVisas createLink(
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

    return linkedAccountService.saveLinkedAccount(extractPassport(userInfo, linkedAccount));
  }

  public void deleteLink(String userId, String providerId) {
    var providerInfo = providerConfig.getServices().get(providerId);

    if (providerInfo == null) {
      throw new NotFoundException(String.format("Provider %s not found", providerId));
    }

    var linkedAccount =
        linkedAccountService
            .getLinkedAccount(userId, providerId)
            .orElseThrow(() -> new NotFoundException("Link not found for user"));

    revokeAccessToken(providerInfo, linkedAccount);

    linkedAccountService.deleteLinkedAccount(userId, providerId);
  }

  private void revokeAccessToken(ProviderInfo providerInfo, LinkedAccount linkedAccount) {
    // Get the endpoint URL and insert the token
    String revokeEndpoint =
        String.format(providerInfo.getRevokeEndpoint(), linkedAccount.getRefreshToken());
    // Add authorization information and make request
    WebClient.ResponseSpec response =
        WebClient.create(revokeEndpoint)
            .post()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .queryParam("client_id", providerInfo.getClientId())
                        .queryParam("client_secret", providerInfo.getClientSecret())
                        .build())
            .retrieve();

    String responseBody =
        response
            .onStatus(HttpStatus::isError, clientResponse -> Mono.empty())
            .bodyToMono(String.class)
            .block(Duration.of(1000, ChronoUnit.MILLIS));

    log.info(
        "Token revocation request for user [{}], provider [{}] returned with the result: [{}]",
        linkedAccount.getUserId(),
        linkedAccount.getProviderId(),
        responseBody);
  }

  private LinkedAccountWithPassportAndVisas extractPassport(
      OAuth2User userInfo, LinkedAccount linkedAccount) {
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
      if (jkuOption.isPresent()) {
        // presence of the jku header means the url it specifies contains the key set that must be
        // used validate the signature
        return ExternalCredsJwtDecoders.fromJku(jkuOption.get()).decode(jwtString);
      } else {
        // no jku means use the issuer to lookup configuration and location of key set
        return JwtDecoders.fromIssuerLocation(issuer).decode(jwtString);
      }
    } catch (ParseException | JwtException | MalformedURLException | IllegalArgumentException e) {
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

  private <T> T getJwtClaim(Jwt jwt, String claimName) {
    T claim = jwt.getClaim(claimName);
    if (claim == null) {
      throw new InvalidJwtException(String.format("jwt missing claim [%s]", claimName));
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
    JSONObject visaClaims = getJwtClaim(visaJwt, GA4GH_VISA_V1_CLAIM);
    var visaType = visaClaims.get(VISA_TYPE_CLAIM);
    if (visaType == null) {
      throw new InvalidJwtException(String.format("visa missing claim [%s]", VISA_TYPE_CLAIM));
    }
    return GA4GHVisa.builder()
        .visaType(visaType.toString())
        .jwt(visaJwt.getTokenValue())
        .expires(getJwtExpires(visaJwt))
        .issuer(visaJwt.getIssuer().toString())
        .lastValidated(new Timestamp(Instant.now().toEpochMilli()))
        .tokenType(determineTokenType(visaJwt))
        .build();
  }
}
