package bio.terra.externalcreds.services;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
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
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenProviderService extends ProviderService {

  private final FenceProviderService fenceProviderService;
  private final AccessTokenCacheService accessTokenCacheService;

  public TokenProviderService(
      ExternalCredsConfig externalCredsConfig,
      ProviderOAuthClientCache providerOAuthClientCache,
      ProviderTokenClientCache providerTokenClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService,
      FenceProviderService fenceProviderService,
      FenceAccountKeyService fenceAccountKeyService,
      AuditLogger auditLogger,
      ObjectMapper objectMapper,
      AccessTokenCacheService accessTokenCacheService) {
    super(
        externalCredsConfig,
        providerOAuthClientCache,
        providerTokenClientCache,
        oAuth2Service,
        linkedAccountService,
        fenceAccountKeyService,
        auditLogger,
        objectMapper);
    this.fenceProviderService = fenceProviderService;
    this.accessTokenCacheService = accessTokenCacheService;
  }

  public LinkedAccount createLink(
      Provider provider,
      String userId,
      String authorizationCode,
      String encodedState,
      AuditLogEvent.Builder auditLogEventBuilder) {

    var oAuth2State = validateOAuth2State(provider, userId, encodedState);
    var providerClient = providerOAuthClientCache.getProviderClient(provider);
    var providerInfo = externalCredsConfig.getProviderProperties(provider);
    try {
      var account =
          createLinkedAccount(
                  provider,
                  userId,
                  authorizationCode,
                  oAuth2State.getRedirectUri(),
                  new HashSet<>(providerInfo.getScopes()),
                  encodedState,
                  providerClient)
              .getLeft();
      var linkedAccount = linkedAccountService.upsertLinkedAccount(account);
      logLinkCreation(Optional.of(linkedAccount), auditLogEventBuilder);
      return linkedAccount;
    } catch (OAuth2AuthorizationException oauthEx) {
      logLinkCreation(Optional.empty(), auditLogEventBuilder);
      throw new BadRequestException(oauthEx);
    }
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

  public String getProviderAccessToken(
      String userId, Provider provider, AuditLogEvent.Builder auditLogEventBuilder) {
    var linkedAccount =
        (switch (provider) {
              case RAS, GITHUB -> linkedAccountService.getLinkedAccount(userId, provider);
              case FENCE, DCF_FENCE, KIDS_FIRST, ANVIL -> fenceProviderService
                  .getLinkedFenceAccount(userId, provider);
            })
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format(
                            "No linked account found for user ID: %s and provider: %s. "
                                + "Please go to the Terra Profile page External Identities tab "
                                + "to link your account for this provider.",
                            userId, provider)));
    return accessTokenCacheService.getLinkedAccountAccessToken(linkedAccount, auditLogEventBuilder);
  }
}
