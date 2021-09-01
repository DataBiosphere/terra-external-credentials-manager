package bio.terra.externalcreds.services;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.ProviderProperties;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import com.google.common.annotations.VisibleForTesting;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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

  public ProviderService(
      ExternalCredsConfig externalCredsConfig,
      ProviderClientCache providerClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService,
      PassportService passportService,
      JwtUtils jwtUtils) {
    this.externalCredsConfig = externalCredsConfig;
    this.providerClientCache = providerClientCache;
    this.oAuth2Service = oAuth2Service;
    this.linkedAccountService = linkedAccountService;
    this.passportService = passportService;
    this.jwtUtils = jwtUtils;
  }

  public Set<String> getProviderList() {
    return Collections.unmodifiableSet(externalCredsConfig.getProviders().keySet());
  }

  public Optional<String> getProviderAuthorizationUrl(
      String provider, String redirectUri, Set<String> scopes, String state) {
    return providerClientCache
        .getProviderClient(provider)
        .map(
            providerClient -> {
              var providerInfo = externalCredsConfig.getProviders().get(provider);

              return oAuth2Service.getAuthorizationRequestUri(
                  providerClient,
                  redirectUri,
                  scopes,
                  state,
                  providerInfo.getAdditionalAuthorizationParameters());
            });
  }

  public Optional<LinkedAccountWithPassportAndVisas> createLink(
      String provider,
      String userId,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state) {

    return providerClientCache
        .getProviderClient(provider)
        .map(
            providerClient ->
                createLinkInternal(
                    provider,
                    userId,
                    authorizationCode,
                    redirectUri,
                    scopes,
                    state,
                    providerClient));
  }

  private LinkedAccountWithPassportAndVisas createLinkInternal(
      String provider,
      String userId,
      String authorizationCode,
      String redirectUri,
      Set<String> scopes,
      String state,
      ClientRegistration providerClient) {
    var providerInfo = externalCredsConfig.getProviders().get(provider);

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
            .providerId(provider)
            .userId(userId)
            .expires(expires)
            .externalUserId(userInfo.getAttribute(EXTERNAL_USERID_ATTR))
            .refreshToken(refreshToken.getTokenValue())
            .isAuthenticated(true)
            .build();

    return linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
        jwtUtils.enrichAccountWithPassportAndVisas(linkedAccount, userInfo));
  }

  public void deleteLink(String userId, String providerId) {
    var providerInfo = externalCredsConfig.getProviders().get(providerId);

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

  @VisibleForTesting
  void authAndRefreshPassport(LinkedAccount linkedAccount) {
    if (linkedAccount.getExpires().before(Timestamp.from(Instant.now()))) {
      passportService.deletePassport(linkedAccount.getId().get());
      linkedAccountService.updateLinkAuthenticationStatus(linkedAccount.getId().get(), false);
    } else {
      try {
        var clientRegistration =
            providerClientCache.getProviderClient(linkedAccount.getProviderId());
        var accessTokenResponse =
            oAuth2Service.authorizeWithRefreshToken(
                clientRegistration.orElseThrow(),
                new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null));
        var userInfo =
            oAuth2Service.getUserInfo(
                clientRegistration.orElseThrow(), accessTokenResponse.getAccessToken());

        // TODO: do we actually need to update the refresh token? If we do, test if it's updated
        // correctly
        // save the linked account with the new refresh token and extracted passport
        var linkedAccountWithRefreshToken =
            linkedAccount.withRefreshToken(accessTokenResponse.getRefreshToken().getTokenValue());
        linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
            jwtUtils.enrichAccountWithPassportAndVisas(linkedAccountWithRefreshToken, userInfo));

      } catch (IllegalArgumentException iae) {
        throw new ExternalCredsException(
            String.format("Could not contact issuer for provider %s", linkedAccount.getProviderId()), iae);
      } catch (OAuth2AuthorizationException oauthEx) {
        // if the cause is a 4xx response, delete the passport
        if (oauthEx.getCause() instanceof HttpClientErrorException) {
          passportService.deletePassport(linkedAccount.getId().get());
          linkedAccountService.updateLinkAuthenticationStatus(linkedAccount.getId().get(), false);
        } else {
          // log and try again later
          throw new ExternalCredsException("Failed to refresh passport: ", oauthEx);
        }
      }
    }
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
        linkedAccount.getProviderId(),
        responseBody);
  }
}
