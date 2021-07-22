package bio.terra.externalcreds.services;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.google.common.annotations.VisibleForTesting;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTParser;
import java.net.MalformedURLException;
import java.net.URI;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

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
        return LocalJwtDecoders.fromJku(jkuOption.get()).decode(jwtString);
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

  /** provides decoders that are not in {@link JwtDecoders} */
  static class LocalJwtDecoders {

    /** Adapted from {@link JwtDecoders#withProviderConfiguration(java.util.Map, String)} */
    public static JwtDecoder fromJku(URI jku) throws MalformedURLException {
      OAuth2TokenValidator<Jwt> jwtValidator = JwtValidators.createDefault();
      RemoteJWKSet<SecurityContext> jwkSource = new RemoteJWKSet<>(jku.toURL());
      Set<SignatureAlgorithm> signatureAlgorithms = getSignatureAlgorithms(jwkSource);
      NimbusJwtDecoder jwtDecoder =
          NimbusJwtDecoder.withJwkSetUri(jku.toString())
              .jwsAlgorithms((algs) -> algs.addAll(signatureAlgorithms))
              .build();
      jwtDecoder.setJwtValidator(jwtValidator);
      return jwtDecoder;
    }

    /**
     * Copied from {@link
     * org.springframework.security.oauth2.jwt.JwtDecoderProviderConfigurationUtils#getSignatureAlgorithms(JWKSource)}
     * because it is private
     */
    private static Set<SignatureAlgorithm> getSignatureAlgorithms(
        JWKSource<SecurityContext> jwkSource) {
      JWKMatcher jwkMatcher =
          new JWKMatcher.Builder()
              .publicOnly(true)
              .keyUses(KeyUse.SIGNATURE, null)
              .keyTypes(KeyType.RSA, KeyType.EC)
              .build();
      Set<JWSAlgorithm> jwsAlgorithms = new HashSet<>();
      try {
        List<? extends JWK> jwks = jwkSource.get(new JWKSelector(jwkMatcher), null);
        for (JWK jwk : jwks) {
          if (jwk.getAlgorithm() != null) {
            JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jwk.getAlgorithm().getName());
            jwsAlgorithms.add(jwsAlgorithm);
          } else {
            if (jwk.getKeyType() == KeyType.RSA) {
              jwsAlgorithms.addAll(JWSAlgorithm.Family.RSA);
            } else if (jwk.getKeyType() == KeyType.EC) {
              jwsAlgorithms.addAll(JWSAlgorithm.Family.EC);
            }
          }
        }
      } catch (KeySourceException ex) {
        throw new IllegalStateException(ex);
      }
      Set<SignatureAlgorithm> signatureAlgorithms = new HashSet<>();
      for (JWSAlgorithm jwsAlgorithm : jwsAlgorithms) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.from(jwsAlgorithm.getName());
        if (signatureAlgorithm != null) {
          signatureAlgorithms.add(signatureAlgorithm);
        }
      }
      Assert.notEmpty(signatureAlgorithms, "Failed to find any algorithms from the JWK set");
      return signatureAlgorithms;
    }
  }
}
