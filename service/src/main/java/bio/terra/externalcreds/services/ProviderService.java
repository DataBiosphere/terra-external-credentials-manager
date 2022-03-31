package bio.terra.externalcreds.services;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.models.CannotDecodeOAuth2State;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.OAuth2State;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ProviderService {

  public static final String EXTERNAL_USERID_ATTR = "email";

  private final ExternalCredsConfig externalCredsConfig;
  private final ProviderClientCache providerClientCache;
  private final OAuth2Service oAuth2Service;
  private final LinkedAccountService linkedAccountService;
  private final PassportService passportService;
  private final JwtUtils jwtUtils;
  private final AuditLogger auditLogger;
  private final SecureRandom secureRandom = new SecureRandom();
  private final ObjectMapper objectMapper;
  private static final Collection<String> unrecoverableOAuth2ErrorCodes =
      Set.of(
          OAuth2ErrorCodes.ACCESS_DENIED,
          OAuth2ErrorCodes.INSUFFICIENT_SCOPE,
          OAuth2ErrorCodes.INVALID_CLIENT,
          OAuth2ErrorCodes.INVALID_GRANT,
          OAuth2ErrorCodes.INVALID_REDIRECT_URI,
          OAuth2ErrorCodes.INVALID_REQUEST,
          OAuth2ErrorCodes.INVALID_SCOPE,
          OAuth2ErrorCodes.INVALID_TOKEN,
          OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
          OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE,
          OAuth2ErrorCodes.UNSUPPORTED_RESPONSE_TYPE,
          OAuth2ErrorCodes.UNSUPPORTED_TOKEN_TYPE);

  public ProviderService(
      ExternalCredsConfig externalCredsConfig,
      ProviderClientCache providerClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService,
      PassportService passportService,
      JwtUtils jwtUtils,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.externalCredsConfig = externalCredsConfig;
    this.providerClientCache = providerClientCache;
    this.oAuth2Service = oAuth2Service;
    this.linkedAccountService = linkedAccountService;
    this.passportService = passportService;
    this.jwtUtils = jwtUtils;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  public Set<String> getProviderList() {
    return Collections.unmodifiableSet(externalCredsConfig.getProviders().keySet());
  }

  /**
   * Get a new passport for each linked accounts with visas or passports expiring within
   * externalCredsConfig.getVisaAndPassportRefreshInterval time from now
   *
   * @return the number of linked accounts with expiring visas or passports
   */
  public int refreshExpiringPassports() {
    var refreshInterval = externalCredsConfig.getVisaAndPassportRefreshDuration();
    var expirationCutoff = new Timestamp(Instant.now().plus(refreshInterval).toEpochMilli());
    var expiringLinkedAccounts = linkedAccountService.getExpiringLinkedAccounts(expirationCutoff);

    for (LinkedAccount linkedAccount : expiringLinkedAccounts) {
      try {
        authAndRefreshPassport(linkedAccount);
      } catch (Exception e) {
        log.info("Failed to refresh passport, will try again at the next interval.", e);
      }
    }

    return expiringLinkedAccounts.size();
  }

  public Optional<String> getProviderAuthorizationUrl(
      String userId, String providerName, String redirectUri, Set<String> scopes) {
    return providerClientCache
        .getProviderClient(providerName)
        .map(
            providerClient -> {
              var providerInfo = externalCredsConfig.getProviders().get(providerName);

              validateRedirectUri(redirectUri, providerInfo);

              // oAuth2State is used to prevent CRSF attacks
              // see https://auth0.com/docs/secure/attack-protection/state-parameters
              // a random value is generated and stored here then validated in createLink below
              var oAuth2State =
                  new OAuth2State.Builder()
                      .provider(providerName)
                      .random(OAuth2State.generateRandomState(secureRandom))
                      .build();
              linkedAccountService.upsertOAuth2State(userId, oAuth2State);

              return oAuth2Service.getAuthorizationRequestUri(
                  providerClient,
                  redirectUri,
                  scopes,
                  oAuth2State.encode(objectMapper),
                  providerInfo.getAdditionalAuthorizationParameters());
            });
  }

  private void validateRedirectUri(String redirectUri, ProviderProperties providerInfo) {
    if (providerInfo.getAllowedRedirectUriPatterns().stream()
        .noneMatch(pattern -> pattern.matcher(redirectUri).matches())) {
      throw new BadRequestException("redirect uri not allowed");
    }
  }

  public Optional<LinkedAccountWithPassportAndVisas> createLink(
      String providerName,
      String userId,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String encodedState) {

    validateOAuth2State(providerName, userId, encodedState);

    return providerClientCache
        .getProviderClient(providerName)
        .map(
            providerClient -> {
              try {
                return createLinkInternal(
                    providerName,
                    userId,
                    authorizationCode,
                    redirectUri,
                    scopes,
                    encodedState,
                    providerClient);
              } catch (OAuth2AuthorizationException oauthEx) {
                throw new BadRequestException(oauthEx);
              }
            });
  }

  private void validateOAuth2State(String providerName, String userId, String encodedState) {
    try {
      OAuth2State oAuth2State = OAuth2State.decode(objectMapper, encodedState);
      if (!providerName.equals(oAuth2State.getProvider())) {
        throw new InvalidOAuth2State();
      }
      linkedAccountService.validateAndDeleteOAuth2State(userId, oAuth2State);
    } catch (CannotDecodeOAuth2State e) {
      throw new InvalidOAuth2State(e);
    }
  }

  private LinkedAccountWithPassportAndVisas createLinkInternal(
      String providerName,
      String userId,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state,
      ClientRegistration providerClient) {
    var providerInfo = externalCredsConfig.getProviders().get(providerName);

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
        new LinkedAccount.Builder()
            .providerName(providerName)
            .userId(userId)
            .expires(expires)
            .externalUserId(userInfo.getAttribute(EXTERNAL_USERID_ATTR))
            .refreshToken(refreshToken.getTokenValue())
            .isAuthenticated(true)
            .build();

    return linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
        jwtUtils.enrichAccountWithPassportAndVisas(linkedAccount, userInfo));
  }

  public void deleteLink(String userId, String providerName) {
    var providerInfo = externalCredsConfig.getProviders().get(providerName);

    if (providerInfo == null) {
      throw new NotFoundException(String.format("Provider %s not found", providerName));
    }

    var linkedAccount =
        linkedAccountService
            .getLinkedAccount(userId, providerName)
            .orElseThrow(() -> new NotFoundException("Link not found for user"));

    revokeAccessToken(providerInfo, linkedAccount);

    linkedAccountService.deleteLinkedAccount(userId, providerName);
  }

  public int validateAccessTokenVisas() {
    var visaDetailsList = passportService.getUnvalidatedAccessTokenVisaDetails();

    var linkedAccountIdsToRefresh =
        visaDetailsList.stream()
            .flatMap(
                visaDetails ->
                    validateVisaWithProvider(visaDetails)
                        ? Stream.empty()
                        : Stream.of(visaDetails.getLinkedAccountId()))
            .distinct();

    linkedAccountIdsToRefresh.forEach(
        linkedAccountId -> {
          var linkedAccount = linkedAccountService.getLinkedAccount(linkedAccountId);
          try {
            linkedAccount.ifPresentOrElse(
                this::authAndRefreshPassport,
                () -> log.info("No linked account found when trying to validate passport."));
          } catch (Exception e) {
            log.info("Failed to refresh passport, will try again at the next interval.", e);
          }
        });

    return visaDetailsList.size();
  }

  @VisibleForTesting
  void authAndRefreshPassport(LinkedAccount linkedAccount) {
    if (linkedAccount.getExpires().toInstant().isBefore(Instant.now())) {
      invalidateLinkedAccount(linkedAccount);
    } else {
      try {
        var linkedAccountWithRefreshedPassport = getRefreshedPassportsAndVisas(linkedAccount);
        linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
            linkedAccountWithRefreshedPassport);

        auditLogger.logEvent(
            new AuditLogEvent.Builder()
                .auditLogEventType(AuditLogEventType.LinkRefreshed)
                .providerName(linkedAccount.getProviderName())
                .userId(linkedAccount.getUserId())
                .build());

      } catch (IllegalArgumentException iae) {
        throw new ExternalCredsException(
            String.format(
                "Could not contact issuer for provider %s", linkedAccount.getProviderName()),
            iae);
      } catch (OAuth2AuthorizationException oauthEx) {
        // if it looks like the refresh token will never work, delete the passport
        if (unrecoverableOAuth2ErrorCodes.contains(getRootOAuth2ErrorCode(oauthEx))) {
          log.info(
              String.format(
                  "Caught unrecoverable oauth2 error code refreshing passport for user id [%s].",
                  linkedAccount.getUserId()),
              oauthEx);
          if (linkedAccount.getId().isEmpty()) {
            throw new ExternalCredsException("linked account id missing");
          }
          invalidateLinkedAccount(linkedAccount);
        } else {
          // log and try again later
          throw new ExternalCredsException("Failed to refresh passport: ", oauthEx);
        }
      }
    }
  }

  /**
   * Traverse causes for oauthEx until the cause is null or there is a cycle in the causes. Return
   * the last oauth2 error code found. This is needed because Spring likes to wrap
   * OAuth2AuthorizationExceptions with other OAuth2AuthorizationExceptions that have non-standard
   * error codes. We just want to handle standard error codes which should be in the root cause.
   */
  private String getRootOAuth2ErrorCode(OAuth2AuthorizationException oauthEx) {
    var errorCode = oauthEx.getError().getErrorCode();
    Throwable currentThrowable = oauthEx.getCause();
    var visitedThrowables = new ArrayList<Throwable>();
    visitedThrowables.add(oauthEx);

    while (currentThrowable != null && !visitedThrowables.contains(currentThrowable)) {
      if (currentThrowable instanceof OAuth2AuthorizationException nestedOauthEx) {
        errorCode = nestedOauthEx.getError().getErrorCode();
      }
      visitedThrowables.add(currentThrowable);
      currentThrowable = currentThrowable.getCause();
    }

    return errorCode;
  }

  private LinkedAccountWithPassportAndVisas getRefreshedPassportsAndVisas(
      LinkedAccount linkedAccount) {
    var clientRegistration =
        providerClientCache
            .getProviderClient(linkedAccount.getProviderName())
            .orElseThrow(
                () ->
                    new ExternalCredsException(
                        String.format(
                            "Unable to find configs for the provider: %s",
                            linkedAccount.getProviderName())));
    var accessTokenResponse =
        oAuth2Service.authorizeWithRefreshToken(
            clientRegistration, new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null));

    // save the linked account with the new refresh token and extracted passport
    var linkedAccountWithRefreshToken =
        Optional.ofNullable(accessTokenResponse.getRefreshToken())
            .map(
                refreshToken ->
                    linkedAccountService.upsertLinkedAccount(
                        linkedAccount.withRefreshToken(refreshToken.getTokenValue())))
            .orElse(linkedAccount);

    // update the passport and visas
    var userInfo =
        oAuth2Service.getUserInfo(clientRegistration, accessTokenResponse.getAccessToken());
    return jwtUtils.enrichAccountWithPassportAndVisas(linkedAccountWithRefreshToken, userInfo);
  }

  @VisibleForTesting
  boolean validateVisaWithProvider(VisaVerificationDetails visaDetails) {
    var providerProperties = externalCredsConfig.getProviders().get(visaDetails.getProviderName());
    if (providerProperties == null) {
      throw new NotFoundException(
          String.format("Provider %s not found", visaDetails.getProviderName()));
    }

    var validationEndpoint =
        providerProperties
            .getValidationEndpoint()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format(
                            "Validation endpoint for provider %s not found",
                            visaDetails.getProviderName())));

    var response =
        WebClient.create(validationEndpoint)
            .get()
            .uri(uriBuilder -> uriBuilder.queryParam("visa", visaDetails.getVisaJwt()).build())
            .retrieve();
    var responseBody =
        response
            .onStatus(HttpStatus::isError, clientResponse -> Mono.empty())
            .bodyToMono(String.class)
            .block(Duration.of(1000, ChronoUnit.MILLIS));

    log.info(
        "Got visa validation response.",
        Map.of(
            "linkedAccountId", visaDetails.getLinkedAccountId(),
            "providerName", visaDetails.getProviderName(),
            "validationResponse", Objects.requireNonNullElse(responseBody, "[null]")));

    var visaValid = "valid".equalsIgnoreCase(responseBody);

    if (visaValid) {
      passportService.updateVisaLastValidated(visaDetails.getVisaId());
    }
    return visaValid;
  }

  private void invalidateLinkedAccount(LinkedAccount linkedAccount) {
    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.LinkExpired)
            .providerName(linkedAccount.getProviderName())
            .userId(linkedAccount.getUserId())
            .build());

    linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
        new LinkedAccountWithPassportAndVisas.Builder()
            .linkedAccount(linkedAccount.withIsAuthenticated(false))
            .passport(Optional.empty()) // explicitly set to empty to be clear about intent
            .build());
  }

  private void revokeAccessToken(
      ProviderProperties providerProperties, LinkedAccount linkedAccount) {
    // Get the endpoint URL and insert the token
    String revokeEndpoint =
        String.format(providerProperties.getRevokeEndpoint(), linkedAccount.getRefreshToken());
    // Add authorization information and make request
    WebClient.ResponseSpec response =
        WebClient.create(revokeEndpoint)
            .post()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .queryParam("client_id", providerProperties.getClientId())
                        .queryParam("client_secret", providerProperties.getClientSecret())
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
        linkedAccount.getProviderName(),
        responseBody);
  }
}
