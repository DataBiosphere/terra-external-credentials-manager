package bio.terra.externalcreds.services;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.LinkedAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenProviderService extends ProviderService {

  public TokenProviderService(
      ExternalCredsConfig externalCredsConfig,
      ProviderClientCache providerClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    super(
        externalCredsConfig,
        providerClientCache,
        oAuth2Service,
        linkedAccountService,
        auditLogger,
        objectMapper);
  }

  public Optional<LinkedAccount> createLink(
      String providerName,
      String userId,
      String authorizationCode,
      String encodedState,
      AuditLogEvent.Builder auditLogEventBuilder) {

    var oAuth2State = validateOAuth2State(providerName, userId, encodedState);

    Optional<LinkedAccount> linkedAccount =
        providerClientCache
            .getProviderClient(providerName)
            .map(
                providerClient -> {
                  var providerInfo = externalCredsConfig.getProviders().get(providerName);
                  try {
                    var account =
                        createLinkedAccount(
                                providerName,
                                userId,
                                authorizationCode,
                                oAuth2State.getRedirectUri(),
                                new HashSet<>(providerInfo.getScopes()),
                                encodedState,
                                providerClient)
                            .getLeft();
                    return linkedAccountService.upsertLinkedAccount(account);
                  } catch (OAuth2AuthorizationException oauthEx) {
                    throw new BadRequestException(oauthEx);
                  }
                });
    logLinkCreation(linkedAccount, auditLogEventBuilder);
    return linkedAccount;
  }

  public void logLinkCreation(
      Optional<LinkedAccount> linkedAccount, AuditLogEvent.Builder auditLogEventBuilder) {
    auditLogger.logEvent(
        auditLogEventBuilder
            .externalUserId(linkedAccount.map(LinkedAccount::getExternalUserId))
            .auditLogEventType(
                linkedAccount
                    .map(x -> AuditLogEventType.LinkCreated)
                    .orElse(AuditLogEventType.LinkCreationFailed))
            .build());
  }

  // much of this is cribbed from passportProviderService.getRefreshedPassportsAndVisas
  // could probably de-dupe this in a refactor
  public Optional<String> getProviderAccessToken(String userId, Provider providerName) {
    // get linked account
    var linkedAccount =
        linkedAccountService
            .getLinkedAccount(userId, providerName.toString())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format(
                            "No linked account found for UserId %s with Provider %s",
                            userId, providerName)));

    // get client registration from provider client cache
    var clientRegistration =
        providerClientCache
            .getProviderClient(providerName.toString())
            .orElseThrow(
                () ->
                    new ExternalCredsException(
                        String.format(
                            "Unable to find configs for the provider: %s", providerName)));

    // make sure refresh token is populated in the linked account
    if (linkedAccount.getRefreshToken().isEmpty()) {
      throw new NotFoundException(
          String.format("No refresh token found for provider %s", providerName));
    }

    // exchange refresh token for access token
    var accessTokenResponse =
        oAuth2Service.authorizeWithRefreshToken(
            clientRegistration, new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null));

    // save the linked account with the new refresh token to replace the old one
    var linkedAccountWithRefreshToken =
        Optional.ofNullable(accessTokenResponse.getRefreshToken())
            .map(
                refreshToken ->
                    linkedAccountService.upsertLinkedAccount(
                        linkedAccount.withRefreshToken(refreshToken.getTokenValue())))
            .orElse(linkedAccount);

    return Optional.of(accessTokenResponse.getAccessToken().getTokenValue());
  }
}
